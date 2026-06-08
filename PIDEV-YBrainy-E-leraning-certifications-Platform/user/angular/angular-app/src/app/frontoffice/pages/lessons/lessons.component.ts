import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CourseDetail, Lesson, ApiCourseProgress } from '../../models/course.models';
import { CourseStoreService } from '../../services/course-store.service';
import { CourseApiService } from '../../services/course-api.service';
import { UserSessionService } from '../../../tracking/user-session.service';
import { courseMediaUrl } from '../../utils/course-media-url';

@Component({
  selector: 'app-lessons',
  standalone: false,
  templateUrl: './lessons.component.html',
  styleUrls: ['./lessons.component.css'],
  host: { style: 'display:block' },
})
export class LessonsComponent implements OnInit, OnDestroy {
  course: CourseDetail | null = null;
  lessons: Lesson[] = [];
  courseId = '';
  visitedLessonIds = new Set<string>();
  courseProgress: ApiCourseProgress | null = null;
  editingLessonId: string | null = null;
  showForm = false;
  lessonsLoading = false;
  lessonsError = '';
  selectedLesson: Lesson | null = null;

  private readonly sub = new Subscription();
  private lessonStartTime: number | null = null;
  private activeTimerLessonId: number | null = null;

  get isStudent(): boolean { return (this.userSession.get()?.role ?? 'STUDENT') === 'STUDENT'; }

  form!: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private store: CourseStoreService,
    private sanitizer: DomSanitizer,
    private api: CourseApiService,
    private userSession: UserSessionService
  ) {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(3)]],
      description: ['', [Validators.required, Validators.minLength(5)]],
      videoUrl: ['', [Validators.required]],
      durationMinutes: [null as number | null, [Validators.min(0), Validators.pattern(/^[0-9]+$/)]],
    });
  }

  ngOnInit(): void {
    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        this.courseId = pm.get('courseId') ?? '';
        const id = this.courseId ? +this.courseId : NaN;
        if (!isNaN(id)) {
          this.checkEnrollmentGate(id);
          this.sub.add(
            this.store.getCourseById(id).subscribe({
              next: (detail) => (this.course = detail),
              error: () => (this.course = null),
            })
          );
          this.loadLessons(id);
        } else {
          this.course = null;
          this.lessons = [];
          this.lessonsError = 'Invalid course id.';
        }
      })
    );

    this.sub.add(
      this.store.selectedCourse$.subscribe((selected) => {
        if (selected && this.courseId && String(selected.id) === this.courseId) {
          this.course = selected;
        }
      })
    );
  }

  startLessonTimer(lessonId: number): void {
    this.stopLessonTimer();
    this.lessonStartTime = Date.now();
    this.activeTimerLessonId = lessonId;
  }

  stopLessonTimer(): void {
    if (this.lessonStartTime && this.activeTimerLessonId) {
      const seconds = Math.floor((Date.now() - this.lessonStartTime) / 1000);
      if (seconds > 3 && this.isStudent) {
        const courseIdNum = +this.courseId;
        const lessonId = this.activeTimerLessonId;
        this.api.trackTimeSpent(courseIdNum, lessonId, (this.userSession.get()?.userId ?? 0), seconds).subscribe();
      }
    }
    this.lessonStartTime = null;
    this.activeTimerLessonId = null;
  }

  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    this.stopLessonTimer();
  }

  ngOnDestroy(): void {
    this.stopLessonTimer();
    this.store.clearSelectedCourse();
    this.sub.unsubscribe();
  }

  // ── Lesson list ───────────────────────────────────────────────────

  get courseLessons(): Lesson[] {
    const lessons = Array.isArray(this.lessons) && this.lessons.length
      ? this.lessons
      : (this.course?.lessons ?? []);
    return lessons
      .slice()
      .sort((a, b) => {
        const ao = typeof a.orderIndex === 'number' ? a.orderIndex : 0;
        const bo = typeof b.orderIndex === 'number' ? b.orderIndex : 0;
        return ao - bo;
      });
  }

  // ── Progress ──────────────────────────────────────────────────────

  get progressPercent(): number {
    if (this.courseProgress?.completionPercentage != null && this.courseProgress.completionPercentage > 0) {
      return Math.round(this.courseProgress.completionPercentage);
    }
    const total = this.courseLessons.length;
    if (!total) return 0;
    const done = this.courseLessons.filter(l => this.isVisited(l)).length;
    return Math.round((done / total) * 100);
  }

  get progressLabel(): string {
    const total = this.courseLessons.length;
    const done = this.courseLessons.filter(l => this.isVisited(l)).length;
    return `${done}/${total}`;
  }

  get completedCount(): number {
    return this.courseLessons.filter(l => this.isVisited(l)).length;
  }

  get progressPercentage(): number {
    return this.progressPercent;
  }

  isVisited(lesson: Lesson): boolean {
    const id = lesson && lesson.id != null ? String(lesson.id) : '';
    return !!id && this.visitedLessonIds.has(id);
  }

  markLessonComplete(lessonId: number): void {
    const lesson = this.courseLessons.find(l => l.id === lessonId);
    if (lesson) {
      this.markVisited(lesson);
      this.selectedLesson = null;
    }
  }

  // ── Multi-content helpers (iterate all content blocks) ────────────

  contentItems(lesson: Lesson): Array<{ type: string; contentUrl: string; orderIndex?: number }> {
    if (Array.isArray(lesson.contents) && lesson.contents.length) {
      return lesson.contents
        .filter(x => x && x.type && x.contentUrl)
        .slice()
        .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    }
    const url = String((lesson.contentUrl || lesson.videoUrl || '') ?? '').trim();
    if (url) {
      const type = String((lesson as any)?.type ?? '').trim().toUpperCase() || this.deriveTypeFromUrl(url);
      return [{ type, contentUrl: url }];
    }
    return [];
  }

  contentKind(item: { type: string; contentUrl: string }): string {
    const t = String(item.type || '').toUpperCase();
    if (t === 'VIDEO' || t === 'VIDEO_UPLOAD') return 'VIDEO';
    if (t === 'YOUTUBE' || t === 'YOUTUBE_EMBED') return 'YOUTUBE';
    if (t === 'PDF') return 'PDF';
    if (t === 'IMAGE') return 'IMAGE';
    const url = (item.contentUrl || '').toLowerCase();
    if (url.includes('youtube.com') || url.includes('youtu.be')) return 'YOUTUBE';
    if (url.endsWith('.pdf')) return 'PDF';
    if (url.endsWith('.png') || url.endsWith('.jpg') || url.endsWith('.jpeg') || url.endsWith('.webp') || url.endsWith('.gif')) return 'IMAGE';
    if (url.endsWith('.mp4') || url.endsWith('.webm') || url.endsWith('.mov')) return 'VIDEO';
    return 'UNKNOWN';
  }

  safeContentYoutubeUrl(item: { type: string; contentUrl: string }): SafeResourceUrl {
    const embed = this.computeYoutubeEmbedUrl(String(item.contentUrl ?? '').trim());
    return this.sanitizer.bypassSecurityTrustResourceUrl(embed);
  }

  safeContentPdfUrl(item: { type: string; contentUrl: string }): SafeResourceUrl {
    const raw = String(item.contentUrl ?? '').trim();
    return this.sanitizer.bypassSecurityTrustResourceUrl(courseMediaUrl(raw));
  }

  contentMediaUrl(item: { type: string; contentUrl: string }): string {
    return courseMediaUrl(item.contentUrl);
  }

  private computeYoutubeEmbedUrl(raw: string): string {
    if (!raw) return '';
    try {
      const u = new URL(raw);
      if (u.hostname.includes('youtu.be')) {
        const id = u.pathname.replace('/', '').trim();
        return id ? `https://www.youtube.com/embed/${id}` : raw;
      }
      if (u.hostname.includes('youtube.com')) {
        const id = u.searchParams.get('v');
        return id ? `https://www.youtube.com/embed/${id}` : raw;
      }
      return raw;
    } catch { return raw; }
  }

  private deriveTypeFromUrl(url: string): string {
    const u = url.toLowerCase();
    if (u.includes('youtube.com') || u.includes('youtu.be')) return 'YOUTUBE';
    if (u.endsWith('.pdf')) return 'PDF';
    if (u.endsWith('.png') || u.endsWith('.jpg') || u.endsWith('.jpeg') || u.endsWith('.webp') || u.endsWith('.gif')) return 'IMAGE';
    if (u.endsWith('.mp4') || u.endsWith('.webm') || u.endsWith('.mov')) return 'VIDEO';
    return 'LESSON';
  }

  // ── Content helpers ───────────────────────────────────────────────

  lessonKind(lesson: Lesson): string {
    const raw = String((lesson as any)?.type ?? '').trim().toUpperCase();
    if (raw.includes('YOUTUBE')) return 'YOUTUBE';
    if (raw.includes('PDF'))     return 'PDF';
    if (raw.includes('IMAGE') || raw.includes('IMG')) return 'IMAGE';
    if (raw.includes('VIDEO'))   return 'VIDEO';
    const url = String((lesson as any)?.videoUrl ?? (lesson as any)?.contentUrl ?? '').toLowerCase();
    if (url.includes('youtube.com') || url.includes('youtu.be')) return 'YOUTUBE';
    if (url.endsWith('.pdf'))  return 'PDF';
    if (url.endsWith('.png') || url.endsWith('.jpg') || url.endsWith('.jpeg') ||
        url.endsWith('.webp') || url.endsWith('.gif')) return 'IMAGE';
    if (url.endsWith('.mp4') || url.endsWith('.webm') || url.endsWith('.mov')) return 'VIDEO';
    return raw || 'LESSON';
  }

  lessonKindIcon(lesson: Lesson): string {
    const k = this.lessonKind(lesson);
    if (k === 'YOUTUBE') return '▶';
    if (k === 'VIDEO')   return '⏵';
    if (k === 'PDF')     return '⧉';
    if (k === 'IMAGE')   return '▣';
    return '•';
  }

  lessonKindClass(lesson: Lesson): string {
    return `kind-${this.lessonKind(lesson).toLowerCase()}`;
  }

  lessonVideoSrc(lesson: Lesson): string | null {
    const raw = (lesson.videoUrl || lesson.contentUrl || '').trim();
    if (!raw) return null;
    return courseMediaUrl(raw);
  }

  youtubeEmbedUrl(lesson: Lesson): SafeResourceUrl {
    const raw = (lesson.videoUrl || lesson.contentUrl || '').trim();
    let videoId = '';
    const watchM = raw.match(/[?&]v=([^&#]+)/);
    const shortM = raw.match(/youtu\.be\/([^?&#]+)/);
    if (watchM)      videoId = watchM[1];
    else if (shortM) videoId = shortM[1];
    const url = videoId ? `https://www.youtube.com/embed/${videoId}?rel=0` : raw;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  pdfSrc(lesson: Lesson): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.lessonVideoSrc(lesson) ?? '');
  }

  // ── Navigation ────────────────────────────────────────────────────

  backToCourses(): void {
    if (!this.courseId) {
      this.router.navigateByUrl('/courses');
      return;
    }
    this.router.navigate(['/courses', this.courseId]);
  }

  openLesson(lesson: Lesson): void {
    this.selectedLesson = lesson;
    this.markVisited(lesson);
    this.startLessonTimer(lesson.id);
  }

  goToLessonDetail(lesson: Lesson): void {
    if (!this.courseId) return;
    this.router.navigate(['/courses', this.courseId, 'lessons', lesson.id]);
  }

  // ── Form helpers (kept for compatibility) ─────────────────────────

  openCreate(): void {
    this.editingLessonId = null;
    this.showForm = true;
    this.form.reset({
      title: '',
      description: '',
      videoUrl: '',
      durationMinutes: null,
    });
  }

  openEdit(lesson: Lesson): void {
    this.editingLessonId = String(lesson.id);
    this.showForm = true;
    this.form.reset({
      title: lesson.title,
      description: lesson.description,
      videoUrl: lesson.videoUrl,
      durationMinutes: lesson.durationMinutes ?? null,
    });
  }

  cancel(): void {
    this.showForm = false;
    this.editingLessonId = null;
  }

  save(): void {
    if (!this.courseId) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (this.editingLessonId) {
      const courseIdNum = +this.courseId;
      this.store.updateLesson(this.courseId, this.editingLessonId, {
        title: v.title!,
        description: v.description!,
        videoUrl: v.videoUrl!,
        durationMinutes: v.durationMinutes ?? undefined,
      }).subscribe({
        next: () => this.loadLessons(courseIdNum),
        error: (err) => console.error('Error updating lesson:', err),
      });
    } else {
      const courseIdNum = +this.courseId;
      this.store.addLesson(this.courseId, {
        title: v.title!,
        description: v.description!,
        videoUrl: v.videoUrl!,
        durationMinutes: v.durationMinutes ?? undefined,
      }).subscribe({
        next: () => this.loadLessons(courseIdNum),
        error: (err) => console.error('Error adding lesson:', err),
      });
    }
    this.cancel();
  }

  remove(lesson: Lesson): void {
    if (!this.courseId) return;
    const ok = confirm(`Delete lesson "${lesson.title}"?`);
    if (!ok) return;
    const courseIdNum = +this.courseId;
    this.store.deleteLesson(this.courseId, String(lesson.id)).subscribe({
      next: () => this.loadLessons(courseIdNum),
      error: (err) => console.error('Error deleting lesson:', err),
    });
  }

  // ── Private ───────────────────────────────────────────────────────

  private checkEnrollmentGate(courseId: number): void {
    const role = (this.userSession.get()?.role ?? 'STUDENT');
    if (role !== 'STUDENT') {
      this.loadProgress(courseId);
      return;
    }
    const userId = (this.userSession.get()?.userId ?? 0);
    if (userId <= 0) {
      this.router.navigate(['/courses', courseId]);
      return;
    }
    this.api.getStudentEnrollments(userId).subscribe({
      next: (enrollments) => {
        const isEnrolled = enrollments.some(e => e.courseId === courseId);
        if (!isEnrolled) {
          this.router.navigate(['/courses', courseId]);
          return;
        }
        this.loadProgress(courseId);
      },
      error: () => { this.router.navigate(['/courses', courseId]); },
    });
  }

  private loadProgress(courseId: number): void {
    const userId = this.userSession.get()?.userId ?? 0;
    if (userId <= 0) return;
    this.api.getCourseProgress(courseId, userId).subscribe({
      next: (progress) => {
        console.log('[loadProgress] raw response:', JSON.stringify(progress));
        this.courseProgress = progress;
        const next = new Set<string>();
        progress.lessonProgresses
          .filter(lp => lp.status === 'COMPLETED')
          .forEach(lp => next.add(String(lp.lessonId)));
        this.visitedLessonIds = next;
      },
      error: (err) => { console.error('[loadProgress] error:', err); },
    });
  }

  private markVisited(lesson: Lesson): void {
    const id = lesson && lesson.id != null ? String(lesson.id) : '';
    if (!id) return;
    const isStudent = (this.userSession.get()?.role ?? 'STUDENT') === 'STUDENT';
    const userId = (this.userSession.get()?.userId ?? 0);
    if (isStudent && this.courseId && userId > 0) {
      this.api.markLessonComplete(+this.courseId, lesson.id, userId).subscribe({
        next: (progress) => {
          this.courseProgress = progress;
          const next = new Set<string>();
          progress.lessonProgresses
            .filter(lp => lp.status === 'COMPLETED')
            .forEach(lp => next.add(String(lp.lessonId)));
          this.visitedLessonIds = next;
        },
        error: () => {
          if (!this.visitedLessonIds.has(id)) {
            const next = new Set(this.visitedLessonIds);
            next.add(id);
            this.visitedLessonIds = next;
          }
        },
      });
    } else if (!this.visitedLessonIds.has(id)) {
      const next = new Set(this.visitedLessonIds);
      next.add(id);
      this.visitedLessonIds = next;
    }
  }

  private loadLessons(courseId: number): void {
    this.lessonsLoading = true;
    this.lessonsError = '';
    this.sub.add(
      this.store.getLessonsByCourseId(courseId).subscribe({
        next: (items) => {
          this.lessons = Array.isArray(items) ? items : [];
          this.lessonsLoading = false;
        },
        error: () => {
          this.lessons = [];
          this.lessonsLoading = false;
          this.lessonsError = 'Failed to load lessons.';
        },
      })
    );
  }
}
