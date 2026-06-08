import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { CourseDetail, Lesson } from '../../models/course.models';
import { CourseStoreService } from '../../services/course-store.service';
import { CourseApiService } from '../../services/course-api.service';
import { AiReaderService } from '../../services/ai-reader.service';
import { UserSessionService } from '../../../tracking/user-session.service';
import { courseMediaUrl } from '../../utils/course-media-url';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import confetti from 'canvas-confetti';

@Component({
  selector: 'app-lesson-detail',
  standalone: false,
  templateUrl: './lesson-detail.component.html',
  styleUrls: ['./lesson-detail.component.css'],
  host: { style: 'display:block' },
})
export class LessonDetailComponent implements OnInit, OnDestroy {
  course: CourseDetail | null = null;
  lesson: Lesson | null = null;
  courseLessons: Lesson[] = [];
  activeLessonIndex = -1;

  courseId = '';
  lessonId = '';

  items: Array<{ id?: number; type: string; contentUrl: string; orderIndex: number }> = [];
  activeIndex = 0;
  completed = new Set<number>();

  focusMode = false;
  playbackRate = 1;
  imageZoom = 1;

  liquidFlip = false;

  safeYoutubeUrl: SafeResourceUrl | null = null;
  safePdfUrl: SafeResourceUrl | null = null;
  readerOpen = false;
  readerLoading = false;
  readerFrameUrl: SafeResourceUrl | null = null;
  readerError = '';
  readerSpeakerSource = '';

  private readonly sub = new Subscription();

  @ViewChild('stageVideo') stageVideo?: ElementRef<HTMLVideoElement>;
  @ViewChild('stageImage') stageImage?: ElementRef<HTMLImageElement>;
  private ambientTimer: any;

  private pendingActiveIndex: number | null = null;
  private lessonStartTime: number | null = null;
  private activeTimerLessonId: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private store: CourseStoreService,
    private sanitizer: DomSanitizer,
    private api: CourseApiService,
    private aiReader: AiReaderService,
    private userSession: UserSessionService
  ) {}

  ngOnInit(): void {
    document.body.classList.add('fo-header-fixed');
    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        this.courseId = pm.get('courseId') ?? '';
        this.lessonId = pm.get('lessonId') ?? '';
        this.closeReader();

        const cid = this.courseId ? +this.courseId : NaN;
        if (isNaN(cid)) {
          this.course = null;
          this.lesson = null;
          return;
        }

        this.checkEnrollmentGate(cid);
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

  private checkEnrollmentGate(courseId: number): void {
    const role = (this.userSession.get()?.role ?? 'STUDENT');
    if (role !== 'STUDENT') {
      this.loadLessonData();
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
        this.loadLessonData();
      },
      error: () => { this.router.navigate(['/courses', courseId]); },
    });
  }

  private loadLessonData(): void {
    const cid = this.courseId ? +this.courseId : NaN;
    if (isNaN(cid)) return;

    // Load course for header/breadcrumb (optional)
    this.sub.add(
      this.store.getCourseById(cid).subscribe({
        next: (detail) => {
          this.course = detail;
          this.courseLessons = this.sortCourseLessons(detail?.lessons ?? []);
          this.activeLessonIndex = this.findLessonIndex(this.courseLessons, this.lessonId);
        },
        error: () => (this.course = null),
      })
    );

    // Load lessons list (authoritative) for cross-lesson navigation
    this.sub.add(
      this.store.getLessonsByCourseId(cid).subscribe({
        next: (items) => {
          this.courseLessons = this.sortCourseLessons(items);
          this.activeLessonIndex = this.findLessonIndex(this.courseLessons, this.lessonId);
        },
        error: () => {
          if (!this.courseLessons.length) this.courseLessons = [];
          this.activeLessonIndex = this.findLessonIndex(this.courseLessons, this.lessonId);
        },
      })
    );

    // Load lesson detail (includes contents sequence)
    const lid = this.lessonId ? +this.lessonId : NaN;
    if (!isNaN(lid)) {
      this.sub.add(
        this.store.getLessonById(cid, lid).subscribe({
          next: (lesson) => {
            this.lesson = lesson;
            this.items = this.buildSequenceItems(lesson);
            const idx = this.pendingActiveIndex;
            this.pendingActiveIndex = null;
            this.activeIndex = this.clampIndex(idx ?? 0, this.items.length);
            this.completed.clear();
            this.onActiveItemChanged();
            this.markLessonVisited();
            this.startLessonTimer(lesson.id);
          },
          error: () => {
            this.lesson = null;
            this.items = [];
          },
        })
      );
    } else {
      this.lesson = null;
      this.items = [];
    }
  }

  startLessonTimer(lessonId: number): void {
    this.stopLessonTimer();
    this.lessonStartTime = Date.now();
    this.activeTimerLessonId = lessonId;
  }

  stopLessonTimer(): void {
    if (this.lessonStartTime && this.activeTimerLessonId) {
      const seconds = Math.floor((Date.now() - this.lessonStartTime) / 1000);
      if (seconds > 3 && (this.userSession.get()?.role ?? 'STUDENT') === 'STUDENT') {
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
    document.body.classList.remove('fo-header-fixed');
    this.stopLessonTimer();
    if (this.ambientTimer) {
      clearInterval(this.ambientTimer);
      this.ambientTimer = null;
    }
    this.sub.unsubscribe();
  }

  backToLessons(): void {
    if (!this.courseId) {
      this.router.navigateByUrl('/courses');
      return;
    }
    this.router.navigate(['/courses', this.courseId, 'lessons']);
  }

  backToCourse(): void {
    if (!this.courseId) {
      this.router.navigateByUrl('/courses');
      return;
    }
    this.router.navigate(['/courses', this.courseId]);
  }

  toggleFocusMode(): void {
    this.focusMode = !this.focusMode;
  }

  toggleReader(): void {
    if (this.readerOpen) {
      this.closeReader();
      return;
    }
    this.openReader();
  }

  openReader(): void {
    const cid = this.courseId ? +this.courseId : NaN;
    const lid = this.lessonId ? +this.lessonId : NaN;
    if (isNaN(cid) || isNaN(lid) || this.readerLoading) return;

    this.readerLoading = true;
    this.readerError = '';
    this.sub.add(
      this.aiReader.getLessonReaderFrame(cid, lid).subscribe({
        next: (frame) => {
          this.readerFrameUrl = frame.url;
          this.readerSpeakerSource = frame.context.speakerSource === 'TEACHER_SAMPLE'
            ? 'Teacher voice'
            : 'Harvard voice';
          this.readerOpen = true;
          this.readerLoading = false;
        },
        error: () => {
          this.readerLoading = false;
          this.readerOpen = false;
          this.readerError = 'Avatar reader is unavailable.';
        },
      })
    );
  }

  closeReader(): void {
    this.readerOpen = false;
    this.readerFrameUrl = null;
    this.readerError = '';
    this.readerSpeakerSource = '';
  }

  prev(): void {
    if (!this.canPrev) return;
    if (this.activeIndex > 0) {
      this.markCompleted(this.activeIndex);
      this.activeIndex -= 1;
      this.onActiveItemChanged(true);
      return;
    }
    this.goToAdjacentLesson(-1, 'end');
  }

  next(): void {
    if (!this.canNext) return;
    if (this.activeIndex < this.items.length - 1) {
      this.markCompleted(this.activeIndex);
      this.activeIndex += 1;
      this.onActiveItemChanged(true);
      return;
    }
    this.goToAdjacentLesson(1, 'start');
  }

  completeLesson(): void {
    const userId = this.userSession.get()?.userId ?? 0;
    const lid = this.lesson?.id;

    if (!this.courseId || !lid || !userId) {
      return;
    }

    // Mark completed locally first (for immediate UI feedback)
    this.markCompleted(this.activeIndex);
    this.fireConfetti();

    // Call backend to persist completion
    this.api.markLessonComplete(+this.courseId, +lid, userId).subscribe({
      next: (progress) => {
        console.log('Lesson marked complete, new progress:', progress.completionPercentage);
        // Optional: emit event or update parent component's progress display
      },
      error: (err) => {
        console.error('Failed to mark lesson complete:', err);
        // Optionally: remove from local completed set on error
        this.completed.delete(this.activeIndex);
      }
    });
  }

  setActive(index: number): void {
    if (index < 0 || index >= this.items.length) return;
    if (index !== this.activeIndex) this.markCompleted(this.activeIndex);
    this.activeIndex = index;
    this.onActiveItemChanged(true);
  }

  get progressPercent(): number {
    if (!this.items.length) return 0;
    const done = this.completed.size;
    return Math.min(100, Math.max(0, Math.round((done / this.items.length) * 100)));
  }

  get isLast(): boolean {
    return this.isCourseEnd;
  }

  get isCourseEnd(): boolean {
    const atLastItem = this.items.length > 0 && this.activeIndex === this.items.length - 1;
    const atLastLesson = this.activeLessonIndex >= 0 && this.activeLessonIndex === this.courseLessons.length - 1;
    if (!this.courseLessons.length) return atLastItem;
    return atLastItem && atLastLesson;
  }

  get canPrev(): boolean {
    if (this.activeIndex > 0) return true;
    return this.activeLessonIndex > 0;
  }

  get canNext(): boolean {
    if (this.activeIndex < this.items.length - 1) return true;
    if (!this.courseLessons.length) return false;
    return this.activeLessonIndex >= 0 && this.activeLessonIndex < this.courseLessons.length - 1;
  }

  get activeItem(): { id?: number; type: string; contentUrl: string; orderIndex: number } | null {
    return this.items[this.activeIndex] ?? null;
  }

  isCompleted(index: number): boolean {
    return this.completed.has(index);
  }

  iconFor(type: string): string {
    const t = String(type || '').toUpperCase();
    if (t.includes('VIDEO')) return 'play_circle';
    if (t.includes('PDF')) return 'picture_as_pdf';
    if (t.includes('IMAGE')) return 'image';
    if (t.includes('YOUTUBE')) return 'smart_display';
    return 'insert_drive_file';
  }

  stageKind(): 'VIDEO' | 'YOUTUBE' | 'PDF' | 'IMAGE' | 'UNKNOWN' {
    const item = this.activeItem;
    if (!item) return 'UNKNOWN';
    const t = String(item.type || '').toUpperCase();
    if (t === 'VIDEO') return 'VIDEO';
    if (t === 'VIDEO_UPLOAD') return 'VIDEO';
    if (t === 'YOUTUBE' || t === 'YOUTUBE_EMBED') return 'YOUTUBE';
    if (t === 'PDF') return 'PDF';
    if (t === 'IMAGE') return 'IMAGE';
    return 'UNKNOWN';
  }

  mediaUrl(raw: string | null | undefined): string {
    return this.toCourseFileUrl(String(raw ?? ''));
  }

  setPlaybackRate(rate: number): void {
    const r = Number(rate);
    this.playbackRate = !isFinite(r) || r <= 0 ? 1 : r;
    const v = this.stageVideo?.nativeElement;
    if (v) v.playbackRate = this.playbackRate;
  }

  resetZoom(): void {
    this.imageZoom = 1;
  }

  zoomIn(): void {
    this.imageZoom = Math.min(4, Math.round((this.imageZoom + 0.25) * 100) / 100);
  }

  zoomOut(): void {
    this.imageZoom = Math.max(1, Math.round((this.imageZoom - 0.25) * 100) / 100);
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(ev: KeyboardEvent): void {
    if (ev.key === 'ArrowLeft') {
      ev.preventDefault();
      this.prev();
      return;
    }
    if (ev.key === 'ArrowRight') {
      ev.preventDefault();
      this.next();
      return;
    }
    if (ev.key === 'f' || ev.key === 'F') {
      this.toggleFocusMode();
      return;
    }
  }

  private markCompleted(index: number): void {
    if (index < 0 || index >= this.items.length) return;
    this.completed.add(index);
  }

  private sortCourseLessons(lessons: Lesson[]): Lesson[] {
    return (Array.isArray(lessons) ? lessons : [])
      .slice()
      .sort((a, b) => {
        const ao = a && typeof a.orderIndex === 'number' ? a.orderIndex : 0;
        const bo = b && typeof b.orderIndex === 'number' ? b.orderIndex : 0;
        return ao - bo;
      });
  }

  private findLessonIndex(lessons: Lesson[], lessonId: string): number {
    const lid = String(lessonId ?? '').trim();
    if (!lid) return -1;
    return (Array.isArray(lessons) ? lessons : []).findIndex((l) => l && l.id != null && String(l.id) === lid);
  }

  private clampIndex(idx: number, len: number): number {
    if (!len) return 0;
    if (!isFinite(idx)) return 0;
    return Math.min(len - 1, Math.max(0, Math.trunc(idx)));
  }

  private goToAdjacentLesson(delta: 1 | -1, position: 'start' | 'end'): void {
    if (!this.courseId) return;
    if (!this.courseLessons.length) return;

    const nextLessonIndex = this.activeLessonIndex + delta;
    if (nextLessonIndex < 0 || nextLessonIndex >= this.courseLessons.length) return;
    const nextLesson = this.courseLessons[nextLessonIndex];
    if (!nextLesson || nextLesson.id == null) return;

    this.pendingActiveIndex = position === 'end' ? Number.MAX_SAFE_INTEGER : 0;
    this.router.navigate(['/courses', this.courseId, 'lessons', String(nextLesson.id)]);
  }

  private visitedKey(): string {
    return `fo.course.${this.courseId}.visitedLessons`;
  }

  private markLessonVisited(): void {
    const lid = this.lessonId ? String(this.lessonId) : '';
    if (!lid || !this.courseId) return;
    try {
      const raw = localStorage.getItem(this.visitedKey());
      const arr = raw ? (JSON.parse(raw) as unknown) : [];
      const set = new Set<string>();
      if (Array.isArray(arr)) arr.forEach((x) => x != null && set.add(String(x)));
      if (!set.has(lid)) {
        set.add(lid);
        localStorage.setItem(this.visitedKey(), JSON.stringify(Array.from(set)));
      }
    } catch {
      // ignore
    }
    const userId = (this.userSession.get()?.userId ?? 0);
    if (this.courseId && lid && userId) {
      this.api.markLessonComplete(+this.courseId, +lid, userId).subscribe({
        next: (progress) => {
          // If this lesson is already marked COMPLETED in the backend,
          // pre-fill all content items as done so progressPercent survives a refresh.
          const thisLessonCompleted = (progress.lessonProgresses ?? [])
            .some((lp: any) => String(lp.lessonId) === lid && lp.status === 'COMPLETED');
          if (thisLessonCompleted) {
            this.items.forEach((_, i) => this.completed.add(i));
          }
        }
      });
    }
  }

  private buildSequenceItems(lesson: Lesson): Array<{ id?: number; type: string; contentUrl: string; orderIndex: number }> {
    const seq = Array.isArray(lesson.contents) ? lesson.contents : [];

    const normalized = (t: any): string => {
      const s = String(t || '').toUpperCase();
      if (s === 'VIDEO') return 'VIDEO_UPLOAD';
      if (s === 'YOUTUBE') return 'YOUTUBE_EMBED';
      return s;
    };

    if (seq.length) {
      return seq
        .filter((x) => x && x.type && x.contentUrl)
        .map((x) => ({ id: x.id, type: normalized(x.type), contentUrl: x.contentUrl, orderIndex: typeof x.orderIndex === 'number' ? x.orderIndex : 0 }))
        .sort((a, b) => a.orderIndex - b.orderIndex);
    }

    const legacyUrl = String((lesson.contentUrl || lesson.videoUrl || '') ?? '').trim();
    const legacyType = normalized(lesson.type);
    if (legacyUrl && legacyType) {
      return [{ type: legacyType, contentUrl: legacyUrl, orderIndex: 0 }];
    }
    return [];
  }

  private toCourseFileUrl(raw: string): string {
    return courseMediaUrl(raw);
  }

  private computeYoutubeEmbed(url: string): string {
    const raw = String(url ?? '').trim();
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
    } catch {
      return raw;
    }
  }

  private onActiveItemChanged(fromNav = false): void {
    if (fromNav) this.liquidFlip = !this.liquidFlip;

    this.safeYoutubeUrl = null;
    this.safePdfUrl = null;
    this.imageZoom = 1;

    const item = this.activeItem;
    if (!item) return;

    if (this.ambientTimer) {
      clearInterval(this.ambientTimer);
      this.ambientTimer = null;
    }

    const kind = this.stageKind();
    if (kind === 'YOUTUBE') {
      const embed = this.computeYoutubeEmbed(item.contentUrl);
      this.safeYoutubeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(embed);
      this.setAmbientFallback('rgba(54,93,247,.55)', 'rgba(17,24,39,.92)');
      return;
    }

    if (kind === 'PDF') {
      const raw = String(item.contentUrl ?? '').trim();
      this.safePdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(courseMediaUrl(raw));
      this.setAmbientFallback('rgba(17,24,39,.92)', 'rgba(54,93,247,.45)');
      return;
    }

    if (kind === 'IMAGE') {
      // ambient from image once loaded
      this.setAmbientFallback('rgba(54,93,247,.40)', 'rgba(17,24,39,.90)');
      return;
    }

    if (kind === 'VIDEO') {
      // sample a frame every ~900ms
      this.setAmbientFallback('rgba(17,24,39,.92)', 'rgba(54,93,247,.45)');
      this.ambientTimer = setInterval(() => this.sampleVideoAmbient(), 900);
      setTimeout(() => {
        const v = this.stageVideo?.nativeElement;
        if (v) v.playbackRate = this.playbackRate;
      }, 0);
      return;
    }

    this.setAmbientFallback('rgba(17,24,39,.92)', 'rgba(54,93,247,.45)');
  }

  onStageImageLoaded(): void {
    this.sampleImageAmbient();
  }

  private setAmbientFallback(c1: string, c2: string): void {
    document.documentElement.style.setProperty('--theater-ambient-1', c1);
    document.documentElement.style.setProperty('--theater-ambient-2', c2);
  }

  private sampleImageAmbient(): void {
    const img = this.stageImage?.nativeElement;
    if (!img) return;
    try {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      const w = 64;
      const h = 64;
      canvas.width = w;
      canvas.height = h;
      ctx.drawImage(img, 0, 0, w, h);
      const data = ctx.getImageData(0, 0, w, h).data;
      let r = 0, g = 0, b = 0, c = 0;
      for (let i = 0; i < data.length; i += 16) {
        r += data[i];
        g += data[i + 1];
        b += data[i + 2];
        c += 1;
      }
      r = Math.round(r / c);
      g = Math.round(g / c);
      b = Math.round(b / c);
      this.setAmbientFallback(`rgba(${r}, ${g}, ${b}, .35)`, 'rgba(17,24,39,.92)');
    } catch {
      this.setAmbientFallback('rgba(54,93,247,.40)', 'rgba(17,24,39,.90)');
    }
  }

  private sampleVideoAmbient(): void {
    const v = this.stageVideo?.nativeElement;
    if (!v || v.readyState < 2) return;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const w = 64;
    const h = 64;
    canvas.width = w;
    canvas.height = h;
    try {
      ctx.drawImage(v, 0, 0, w, h);
      const data = ctx.getImageData(0, 0, w, h).data;
      let r = 0, g = 0, b = 0, c = 0;
      for (let i = 0; i < data.length; i += 16) {
        r += data[i];
        g += data[i + 1];
        b += data[i + 2];
        c += 1;
      }
      r = Math.round(r / c);
      g = Math.round(g / c);
      b = Math.round(b / c);
      this.setAmbientFallback(`rgba(${r}, ${g}, ${b}, .28)`, 'rgba(17,24,39,.92)');
    } catch (e) {
      // Cross-origin content (YouTube iframe) — skip ambient silently
      clearInterval(this.ambientTimer);
      this.ambientTimer = null;
    }
  }

  private fireConfetti(): void {
    try {
      confetti({
        particleCount: 130,
        spread: 70,
        origin: { y: 0.72 },
      });
      setTimeout(() => {
        confetti({ particleCount: 60, spread: 100, origin: { y: 0.62 } });
      }, 280);
    } catch {
      // ignore
    }
  }
}
