import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Course, CourseDetail, CourseRequestDTO, Lesson } from '../models/course.models';
import { CourseApiService, ApiCourse, ApiLesson, ApiLessonDetail } from './course-api.service';
import { courseMediaUrl } from '../utils/course-media-url';

function normalizeCourseMediaUrl(raw: string | undefined | null): string {
  return courseMediaUrl(raw);
}

function mapLesson(api: { id: number; title: string; description?: string; type?: string; videoUrl?: string; durationMinutes?: number }): Lesson {
  return {
    id: Number(api.id),
    title: api.title ?? '',
    description: api.description ?? '',
    type: api.type,
    contentUrl: (api as any).contentUrl,
    videoUrl: api.videoUrl,
    durationMinutes: api.durationMinutes,
  };
}

function mapApiLessonToLesson(api: ApiLesson): Lesson {
  return {
    id: Number(api.id),
    title: api.title ?? '',
    description: api.description ?? '',
    type: api.type,
    contentUrl: (api as any).contentUrl ?? (api as any).fileUrl,
    videoUrl: api.videoUrl,
    orderIndex: typeof api.orderIndex === 'number' ? api.orderIndex : (api as any).orderIndex,
    durationMinutes: api.durationMinutes,
  };
}

function mapApiLessonDetailToLesson(api: ApiLessonDetail): Lesson {
  const base = mapApiLessonToLesson(api);
  return {
    ...base,
    ...(Array.isArray(api.contents) ? { contents: api.contents } : {}),
  } as any;
}

/** Map list item (CourseResponseDTO, no lessons) from Page.content. */
function mapApiCourseToCourse(api: ApiCourse): Course {
  return {
    id: Number(api.id),
    title: api.title ?? '',
    description: api.description ?? '',
    thumbnailUrl: normalizeCourseMediaUrl(api.thumbnailUrl),
    teacherVoiceSamplePath: api.teacherVoiceSamplePath,
    price: api.price ?? 0,
    rating: api.rating ?? 0,
    ratingCount: api.ratingCount ?? 0,
    category: api.category ?? '',
    level: api.level ?? 'BEGINNER',
    approximateDurationMinutes: api.approximateDurationMinutes ?? 0,
    isPublished: api.isPublished ?? true,
    offersCertificate: api.offersCertificate,
    lessonCount: api.lessonCount ?? 0,
    createdAt: api.createdAt,
    updatedAt: api.updatedAt,
  };
}

/** Map full course with lessons from GET /api/courses/{id}. */
function mapApiCourseToCourseDetail(api: ApiCourse): CourseDetail {
  const lessons: Lesson[] = Array.isArray(api.lessons) ? api.lessons.map(mapLesson) : [];
  return {
    id: Number(api.id),
    title: api.title ?? '',
    description: api.description ?? '',
    thumbnailUrl: normalizeCourseMediaUrl(api.thumbnailUrl),
    teacherVoiceSamplePath: api.teacherVoiceSamplePath,
    price: api.price ?? 0,
    rating: api.rating ?? 0,
    ratingCount: api.ratingCount ?? 0,
    category: api.category ?? '',
    level: api.level ?? 'BEGINNER',
    approximateDurationMinutes: api.approximateDurationMinutes ?? 0,
    isPublished: api.isPublished ?? true,
    offersCertificate: api.offersCertificate,
    lessonCount: api.lessonCount ?? 0,
    createdAt: api.createdAt,
    updatedAt: api.updatedAt,
    lessons,
  };
}

@Injectable({ providedIn: 'root' })
export class CourseStoreService {
  private readonly _courses$ = new BehaviorSubject<Course[]>([]);
  readonly courses$ = this._courses$.asObservable();

  /** Populated only when the detail view is active (lazy-loaded). */
  private readonly _selectedCourse$ = new BehaviorSubject<CourseDetail | null>(null);
  readonly selectedCourse$ = this._selectedCourse$.asObservable();

  constructor(private apiService: CourseApiService) {
    this.loadCourses();
  }

  get snapshot(): Course[] {
    return this._courses$.value;
  }

  /**
   * Load courses from backend. Backend returns a Spring Data Page; we extract the array
   * from data.content and map each item to Course. Default to [] if content is missing.
   */
  loadCourses(): void {
    this.apiService.listPage().subscribe({
      next: (data) => {
        const content = data?.content != null && Array.isArray(data.content) ? data.content : [];
        const courses: Course[] = content.map((item) => mapApiCourseToCourse(item));
        this._courses$.next(courses);
      },
      error: (err) => {
        console.error('Failed to load courses from Gateway!', err);
        this._courses$.next([]);
      },
    });
  }

  /** Alias for loadCourses; refreshes from backend and maps Page.content. */
  refreshFromBackend(): void {
    this.loadCourses();
  }

  getCourse(courseId: string): Course | undefined {
    return this.snapshot.find((c) => String(c.id) === courseId);
  }

  /**
   * Fetch on demand: GET http://localhost:8089/api/courses/{id}.
   * Returns Observable<CourseDetail> and updates selectedCourse state.
   * Components can subscribe to get the lesson list; detail state is set for the active view.
   */
  getCourseById(id: number): Observable<CourseDetail> {
    return this.apiService.getById(id).pipe(
      map((api) => mapApiCourseToCourseDetail(api)),
      tap((detail) => this._selectedCourse$.next(detail))
    );
  }

  /** Fetch lessons list separately: GET /api/courses/{courseId}/lessons */
  getLessonsByCourseId(courseId: number): Observable<Lesson[]> {
    return this.apiService.listLessons(courseId).pipe(
      map((items) => (Array.isArray(items) ? items : []).map(mapApiLessonToLesson))
    );
  }

  getLessonById(courseId: number, lessonId: number): Observable<Lesson> {
    return this.apiService.getLesson(courseId, lessonId).pipe(
      map((api) => mapApiLessonDetailToLesson(api))
    );
  }

  /** Clear selected course when leaving the detail view. */
  clearSelectedCourse(): void {
    this._selectedCourse$.next(null);
  }

  /** Create course: FormData for multipart (e.g. 'course' JSON + 'thumbnail' File). */
  addCourse(courseData: FormData): void {
    this.apiService.createWithFormData(courseData).subscribe({
      next: () => this.refreshFromBackend(),
      error: (err) => console.error('Error adding course:', err),
    });
  }

  /** Update course: FormData for multipart (e.g. 'course' + 'thumbnail'). */
  updateCourse(id: number, courseData: FormData): void {
    this.apiService.updateWithFormData(id, courseData).subscribe({
      next: () => this.refreshFromBackend(),
      error: (err) => console.error('Error updating course:', err),
    });
  }

  deleteCourse(courseId: number): void {
    this.apiService.delete(courseId).subscribe({
      next: () => this.refreshFromBackend(),
      error: (err) => console.error('Error deleting course:', err),
    });
  }

  /** Create lesson: FormData for multipart (lesson JSON + files) */
  addLesson(courseId: string, input: Omit<Lesson, 'id'>): Observable<void> {
    const cid = Number(courseId);
    if (!cid) {
      console.error('Invalid courseId for addLesson');
      return new Observable<void>(obs => { obs.error(new Error('Invalid courseId')); });
    }

    const formData = new FormData();
    const lessonMeta = {
      title: input.title,
      description: input.description,
      durationMinutes: input.durationMinutes,
      orderIndex: input.orderIndex
    };
    formData.append('lesson', new Blob([JSON.stringify(lessonMeta)], { type: 'application/json' }));

    if (input.videoUrl || input.contentUrl) {
      const url = input.videoUrl || input.contentUrl;
      if (url && (url.includes('youtube.com') || url.includes('youtu.be'))) {
        formData.append('youtubeUrls', url);
      }
    }

    return this.apiService.createLesson(cid, formData).pipe(
      tap(() => this.refreshFromBackend()),
      map(() => void 0)
    );
  }

  /** Update lesson: FormData for multipart */
  updateLesson(courseId: string, lessonId: string, patch: Partial<Omit<Lesson, 'id'>>): Observable<void> {
    const cid = Number(courseId);
    const lid = Number(lessonId);
    if (!cid || !lid) {
      console.error('Invalid courseId or lessonId for updateLesson');
      return new Observable<void>(obs => { obs.error(new Error('Invalid courseId or lessonId')); });
    }

    const formData = new FormData();
    const lessonMeta = {
      title: patch.title,
      description: patch.description,
      durationMinutes: patch.durationMinutes,
      orderIndex: patch.orderIndex
    };
    formData.append('lesson', new Blob([JSON.stringify(lessonMeta)], { type: 'application/json' }));

    if (patch.videoUrl || patch.contentUrl) {
      const url = patch.videoUrl || patch.contentUrl;
      if (url && (url.includes('youtube.com') || url.includes('youtu.be'))) {
        formData.append('youtubeUrls', url);
      }
    }

    return this.apiService.updateLesson(cid, lid, formData).pipe(
      tap(() => this.refreshFromBackend()),
      map(() => void 0)
    );
  }

  /** Delete lesson */
  deleteLesson(courseId: string, lessonId: string): Observable<void> {
    const cid = Number(courseId);
    const lid = Number(lessonId);
    if (!cid || !lid) {
      console.error('Invalid courseId or lessonId for deleteLesson');
      return new Observable<void>(obs => { obs.error(new Error('Invalid courseId or lessonId')); });
    }

    return this.apiService.deleteLesson(cid, lid).pipe(
      tap(() => this.refreshFromBackend()),
      map(() => void 0)
    );
  }

  // Helper for internal state updates if needed locally
  private setCourses(courses: Course[]): void {
    this._courses$.next(courses);
  }
}
