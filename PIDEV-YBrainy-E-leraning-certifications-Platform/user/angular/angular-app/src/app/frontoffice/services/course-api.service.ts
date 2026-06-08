import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { from, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  CourseRequestDTO,
  ApiEnrollment,
  ApiCourseProgress,
  ApiLeaderboardEntry,
  ApiQuestion,
  ApiQuiz,
  ApiQuizResponse,
  ApiQuizResult,
  ApiReview,
  RatingDistribution,
  MlRecommendation,
  MlRecommendationsResponse,
  MlQualityResult,
  AiSearchResult,
  LearningPath,
  CheckoutConfirmRequest,
  CheckoutConfirmResponse,
  CheckoutSessionRequest,
  CheckoutSessionResponse,
  EnrollmentCheckResponse,
  CertificateVerification,
  StudentDashboard,
  AiReaderConfig,
  AiReaderContext,
  TalkingHeadSynthesisRequest,
  TalkingHeadSynthesisResponse,
  GazeTrackingStartRequest,
  GazeTrackingStatus,
  GazeTrackingResult,
} from '../models/course.models';

/**
 * API client for Spring Boot backend via API Gateway.
 * - GET    /api/courses  → returns Spring Data Page (use .content)
 * - GET    /api/courses/category/{category}
 * - GET    /api/courses/{id}
 * - POST   /api/courses           (multipart/form-data)
 * - PUT    /api/courses/{id}      (multipart/form-data)
 * - DELETE /api/courses/{id}
 */

/** Spring Data JPA Page response shape */
export interface SpringPage<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  size?: number;
  number?: number;
  first?: boolean;
  last?: boolean;
}

/** Matches backend CourseResponseDTO (from list/detail). */
export interface ApiCourse {
  id: number;
  title: string;
  description: string;
  thumbnailUrl?: string;
  /** Path to certificate template file (stored on server). Used for download. */
  certificatePath?: string;
  /** Stored server-side path for optional teacher voice sample. */
  teacherVoiceSamplePath?: string;
  price: number;
  rating: number;
  ratingCount: number;
  category: string;
  level: string;
  approximateDurationMinutes: number;
  isPublished: boolean;
  offersCertificate?: boolean;
  lessonCount: number;
  createdAt?: string;
  updatedAt?: string;
  lessons?: Array<{ id: number; title: string; description?: string; type?: string; videoUrl?: string; durationMinutes?: number }>;
}

/** Matches backend LessonResponseDTO (minimal shape used in list/detail). */
export interface ApiLesson {
  id: number;
  title: string;
  description?: string;
  type?: string;
  contentUrl?: string;
  videoUrl?: string;
  orderIndex?: number;
  durationMinutes?: number;
}

export interface ApiLessonContent {
  id: number;
  type: string;
  contentUrl: string;
  orderIndex?: number;
  createdAt?: string;
}

export interface ApiLessonDetail extends ApiLesson {
  contents?: ApiLessonContent[];
  courseId?: number;
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class CourseApiService {
  // Use relative URL for proxy support in dev, or direct URL for production
  private readonly baseUrl = '/api/courses';
  private readonly quizBaseUrl = '/api/quizzes';
  private readonly gatewayBaseUrl = (environment.courseApiBaseUrl || environment.apiBaseUrl).replace(/\/$/, '');

  constructor(private http: HttpClient) {}

  /**
   * Fetches courses from the gateway. Backend returns a Spring Data Page.
   * Returns the raw Page so the store can extract data.content.
   */
  listPage(params?: {
    page?: number;
    size?: number;
    search?: string;
    category?: string;
    level?: string;
    isPublished?: boolean;
  }): Observable<SpringPage<ApiCourse>> {
    const qs = new URLSearchParams();
    if (params?.page != null) qs.set('page', String(params.page));
    if (params?.size != null) qs.set('size', String(params.size));
    if (params?.search) qs.set('search', params.search);
    if (params?.category) qs.set('category', params.category);
    if (params?.level) qs.set('level', params.level);
    if (params?.isPublished != null) qs.set('isPublished', String(params.isPublished));
    const url = qs.toString() ? `${this.baseUrl}?${qs.toString()}` : this.baseUrl;

    return this.http.get<SpringPage<ApiCourse>>(url).pipe(
      catchError((err) => {
        console.warn('HttpClient course list failed; retrying public gateway fetch.', err);
        return this.fetchPublicCoursePage(url);
      })
    );
  }

  private fetchPublicCoursePage(url: string): Observable<SpringPage<ApiCourse>> {
    return this.fetchPublicJson<SpringPage<ApiCourse>>(url);
  }

  private fetchPublicJson<T>(url: string, fallback?: T): Observable<T> {
    const absoluteUrl = `${this.gatewayBaseUrl}${url}`;

    return from(
      fetch(absoluteUrl, {
        method: 'GET',
        credentials: 'omit',
        headers: { Accept: 'application/json' },
      }).then(async (response) => {
        if (!response.ok) {
          throw new Error(`Course list request failed with HTTP ${response.status}`);
        }

        const text = await response.text();
        if (!text.trim()) {
          if (fallback !== undefined) return fallback;
          throw new Error('Public gateway returned an empty response');
        }

        return JSON.parse(text) as T;
      })
    );
  }

  private fetchSameOriginOrGatewayJson<T>(url: string, fallback: T): Observable<T> {
    const absoluteUrl = `${this.gatewayBaseUrl}${url}`;

    return from(
      this.fetchJson<T>(url).catch((sameOriginError) => {
        console.warn('Same-origin public fetch failed; retrying gateway.', sameOriginError);
        return this.fetchJson<T>(absoluteUrl);
      }).catch((gatewayError) => {
        console.warn('Gateway public fetch failed; using fallback.', gatewayError);
        return fallback;
      })
    );
  }

  private async fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, {
      method: init?.method ?? 'GET',
      credentials: 'omit',
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...(init?.headers ?? {}),
      },
      body: init?.body,
    });

    const text = await response.text();
    if (!response.ok) {
      const backendMessage = this.extractFetchErrorMessage(text);
      throw new Error(backendMessage || `Request failed with HTTP ${response.status}`);
    }

    if (!text.trim()) {
      throw new Error('Request returned an empty response');
    }

    return JSON.parse(text) as T;
  }

  private extractFetchErrorMessage(text: string): string {
    if (!text.trim()) return '';

    try {
      const parsed = JSON.parse(text) as { message?: string; error?: string };
      return parsed.message || parsed.error || '';
    } catch {
      return text.length <= 160 ? text : '';
    }
  }

  listByCategory(category: string): Observable<SpringPage<ApiCourse>> {
    return this.listPage({ category, page: 0, size: 100 });
  }

  getById(id: number): Observable<ApiCourse> {
    return this.http.get<ApiCourse>(`${this.baseUrl}/${id}`);
  }

  /** GET /api/courses/{courseId}/lessons */
  listLessons(courseId: number): Observable<ApiLesson[]> {
    return this.http.get<ApiLesson[]>(`${this.baseUrl}/${courseId}/lessons`).pipe(
      catchError(() => of([] as ApiLesson[]))
    );
  }

  /** GET /api/courses/{courseId}/lessons/{lessonId} */
  getLesson(courseId: number, lessonId: number): Observable<ApiLessonDetail> {
    return this.http.get<ApiLessonDetail>(`${this.baseUrl}/${courseId}/lessons/${lessonId}`);
  }

  /**
   * POST /api/courses. Sends FormData (e.g. 'course' JSON part + 'thumbnail' File).
   * Use for MultipartFile requirements.
   */
  createWithFormData(courseData: FormData): Observable<ApiCourse> {
    return this.http.post<ApiCourse>(this.baseUrl, courseData);
  }

  /**
   * POST /api/courses. Creates a course with optional thumbnail and certificate files.
   * @param course - Course metadata (title, description, category, level, price, etc.)
   * @param thumbnail - Optional thumbnail image file
   * @param certificate - Optional certificate template file (required if offersCertificate is true)
   */
  create(course: CourseRequestDTO, thumbnail: File | null, certificate?: File | null): Observable<ApiCourse> {
    const formData = new FormData();
    formData.append('course', new Blob([JSON.stringify(course)], { type: 'application/json' }));
    if (thumbnail) {
      formData.append('thumbnail', thumbnail);
    }
    if (certificate) {
      formData.append('certificate', certificate);
    }
    return this.http.post<ApiCourse>(this.baseUrl, formData);
  }

  /**
   * PUT /api/courses/{id}. FormData for multipart (e.g. 'course' + 'thumbnail' + 'certificate').
   * Use for updating courses with file uploads.
   */
  updateWithFormData(id: number, courseData: FormData): Observable<ApiCourse> {
    return this.http.put<ApiCourse>(`${this.baseUrl}/${id}`, courseData);
  }

  /**
   * PUT /api/courses/{id}. Updates a course with optional thumbnail and certificate files.
   * @param id - Course ID to update
   * @param course - Course metadata (title, description, category, level, price, etc.)
   * @param thumbnail - Optional new thumbnail image file (replaces existing)
   * @param certificate - Optional new certificate template file (required if offersCertificate is true and no existing certificate)
   */
  update(id: number, course: CourseRequestDTO, thumbnail: File | null, certificate?: File | null): Observable<ApiCourse> {
    const formData = new FormData();
    formData.append('course', new Blob([JSON.stringify(course)], { type: 'application/json' }));
    if (thumbnail) {
      formData.append('thumbnail', thumbnail);
    }
    if (certificate) {
      formData.append('certificate', certificate);
    }
    return this.http.put<ApiCourse>(`${this.baseUrl}/${id}`, formData);
  }

  /** DELETE /api/courses/{id} */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  // ==================== LESSON CRUD OPERATIONS ====================

  /**
   * POST /api/courses/{courseId}/lessons
   * Creates a new lesson with multipart form data (lesson metadata + files)
   */
  createLesson(courseId: number, lessonData: FormData): Observable<ApiLessonDetail> {
    return this.http.post<ApiLessonDetail>(`${this.baseUrl}/${courseId}/lessons`, lessonData);
  }

  /**
   * PUT /api/courses/{courseId}/lessons/{lessonId}
   * Updates an existing lesson with multipart form data
   */
  updateLesson(courseId: number, lessonId: number, lessonData: FormData): Observable<ApiLessonDetail> {
    return this.http.put<ApiLessonDetail>(`${this.baseUrl}/${courseId}/lessons/${lessonId}`, lessonData);
  }

  /** DELETE /api/courses/{courseId}/lessons/{lessonId} */
  deleteLesson(courseId: number, lessonId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${courseId}/lessons/${lessonId}`);
  }

  // ==================== PROGRESS ====================

  /** GET /api/courses/{courseId}/progress?studentId={studentId} */
  getCourseProgress(courseId: number, studentId: number): Observable<ApiCourseProgress> {
    return this.http.get<ApiCourseProgress>(`${this.baseUrl}/${courseId}/progress?studentId=${studentId}`);
  }

  /** POST /api/courses/{courseId}/lessons/{lessonId}/complete?studentId={studentId} */
  markLessonComplete(courseId: number, lessonId: number, studentId: number): Observable<ApiCourseProgress> {
    return this.http.post<ApiCourseProgress>(
      `${this.baseUrl}/${courseId}/lessons/${lessonId}/complete?studentId=${studentId}`,
      {}
    );
  }

  // ==================== AI READER ====================

  getAiReaderConfig(): Observable<AiReaderConfig> {
    return this.http.get<AiReaderConfig>(`${this.baseUrl}/ai-reader/config`);
  }

  getCourseReaderContext(courseId: number): Observable<AiReaderContext> {
    return this.http.get<AiReaderContext>(`${this.baseUrl}/${courseId}/reader-context`);
  }

  getLessonReaderContext(courseId: number, lessonId: number): Observable<AiReaderContext> {
    return this.http.get<AiReaderContext>(`${this.baseUrl}/${courseId}/lessons/${lessonId}/reader-context`);
  }

  synthesizeTalkingHead(request: TalkingHeadSynthesisRequest): Observable<TalkingHeadSynthesisResponse> {
    return this.http.post<TalkingHeadSynthesisResponse>(`${this.baseUrl}/ai-reader/tts`, request);
  }

  // ==================== QUIZ ====================

  /** GET /api/quizzes?courseId={courseId} */
  getQuizzesByCourse(courseId: number): Observable<ApiQuiz[]> {
    return this.fetchSameOriginOrGatewayJson<ApiQuiz[]>(
      `${this.quizBaseUrl}?courseId=${courseId}`,
      [] as ApiQuiz[]
    );
  }

  /** GET /api/quizzes/{quizId}/questions */
  getQuizQuestions(courseId: number, quizId: number): Observable<ApiQuestion[]> {
    return this.fetchSameOriginOrGatewayJson<ApiQuestion[]>(
      `${this.quizBaseUrl}/${quizId}/questions`,
      [] as ApiQuestion[]
    );
  }

  /** POST /api/quizzes/{quizId}/submit?studentId={studentId} */
  submitQuiz(courseId: number, quizId: number, studentId: number, responses: ApiQuizResponse[]): Observable<ApiQuizResult> {
    const url = `${this.quizBaseUrl}/${quizId}/submit?studentId=${studentId}`;
    const body = { studentId, answers: responses };

    return from(
      this.fetchJson<ApiQuizResult>(`${this.gatewayBaseUrl}${url}`, {
        method: 'POST',
        body: JSON.stringify(body),
      }).catch((gatewayError) => {
        console.warn('Gateway quiz submit failed; retrying same-origin proxy.', gatewayError);
        return this.fetchJson<ApiQuizResult>(url, {
          method: 'POST',
          body: JSON.stringify(body),
        });
      })
    );
  }

  /** GET /api/quizzes/{quizId}/leaderboard */
  getLeaderboard(courseId: number, quizId: number): Observable<ApiLeaderboardEntry[]> {
    return this.fetchSameOriginOrGatewayJson<ApiLeaderboardEntry[]>(
      `${this.quizBaseUrl}/${quizId}/leaderboard`,
      [] as ApiLeaderboardEntry[]
    );
  }

  startGazeTracking(request: GazeTrackingStartRequest): Observable<GazeTrackingStatus> {
    const url = `${this.quizBaseUrl}/gaze/start`;
    return from(
      this.fetchJson<GazeTrackingStatus>(`${this.gatewayBaseUrl}${url}`, {
        method: 'POST',
        body: JSON.stringify(request),
      }).catch((gatewayError) => {
        console.warn('Gateway gaze start failed; retrying same-origin proxy.', gatewayError);
        return this.fetchJson<GazeTrackingStatus>(url, {
          method: 'POST',
          body: JSON.stringify(request),
        });
      })
    );
  }

  getGazeStatus(sessionId: string, studentId?: number, courseId?: number, quizId?: number): Observable<GazeTrackingStatus> {
    const qs = this.gazeContextQuery(studentId, courseId, quizId);
    return this.fetchSameOriginOrGatewayJson<GazeTrackingStatus>(
      `${this.quizBaseUrl}/gaze/status/${encodeURIComponent(sessionId)}${qs}`,
      {
        user_id: sessionId,
        active: false,
        duration_seconds: 0,
        current_focus_score: 0,
        average_focus_score: 0,
        looking_at_screen: true,
        total_away_time_seconds: 0,
        measurements_count: 0,
      }
    );
  }

  stopGazeTracking(sessionId: string, studentId?: number, courseId?: number, quizId?: number): Observable<GazeTrackingResult> {
    const qs = this.gazeContextQuery(studentId, courseId, quizId);
    const url = `${this.quizBaseUrl}/gaze/stop/${encodeURIComponent(sessionId)}${qs}`;
    return from(
      this.fetchJson<GazeTrackingResult>(`${this.gatewayBaseUrl}${url}`, {
        method: 'POST',
      }).catch((gatewayError) => {
        console.warn('Gateway gaze stop failed; retrying same-origin proxy.', gatewayError);
        return this.fetchJson<GazeTrackingResult>(url, {
          method: 'POST',
        });
      })
    );
  }

  private gazeContextQuery(studentId?: number, courseId?: number, quizId?: number): string {
    const qs = new URLSearchParams();
    if (studentId != null) qs.set('studentId', String(studentId));
    if (courseId != null) qs.set('courseId', String(courseId));
    if (quizId != null) qs.set('quizId', String(quizId));
    return qs.toString() ? `?${qs.toString()}` : '';
  }

  // ==================== REVIEWS ====================

  /** GET /api/courses/{courseId}/reviews/stats */
  getRatingStats(courseId: number): Observable<RatingDistribution> {
    return this.http.get<RatingDistribution>(`${this.baseUrl}/${courseId}/reviews/stats`).pipe(
      catchError(() => of({ averageRating: 0, totalReviews: 0, count5: 0, count4: 0, count3: 0, count2: 0, count1: 0 }))
    );
  }

  /** GET /api/courses/{courseId}/reviews */
  getReviews(courseId: number): Observable<SpringPage<ApiReview>> {
    return this.http.get<SpringPage<ApiReview>>(`${this.baseUrl}/${courseId}/reviews`).pipe(
      catchError(() => of({ content: [] as ApiReview[] }))
    );
  }

  /** POST /api/courses/{courseId}/reviews — body: { studentId, rating, comment } */
  addReview(courseId: number, studentId: number, rating: number, comment: string): Observable<ApiReview> {
    return this.http.post<ApiReview>(
      `${this.baseUrl}/${courseId}/reviews`,
      { studentId, rating, comment }
    );
  }

  updateReview(courseId: number, reviewId: number, studentId: number, rating: number, comment: string): Observable<any> {
    return this.http.put(
      `${this.baseUrl}/${courseId}/reviews/${reviewId}?studentId=${studentId}`,
      { rating, comment }
    );
  }

  deleteReview(courseId: number, reviewId: number, studentId: number): Observable<any> {
    return this.http.delete(
      `${this.baseUrl}/${courseId}/reviews/${reviewId}?studentId=${studentId}`
    );
  }

  // ==================== ENROLLMENTS ====================

  private readonly enrollmentUrl = '/api/enrollments';

  /** GET /api/enrollments/student/{studentId} */
  getStudentEnrollments(studentId: number): Observable<ApiEnrollment[]> {
    return this.http.get<ApiEnrollment[]>(`${this.enrollmentUrl}/student/${studentId}`).pipe(
      catchError((err) => {
        console.warn('HttpClient enrollments failed; retrying public gateway fetch.', err);
        return this.fetchPublicJson<ApiEnrollment[]>(`${this.enrollmentUrl}/student/${studentId}`, [] as ApiEnrollment[]);
      })
    );
  }

  /** POST /api/enrollments — body: { studentId, courseId } */
  enroll(studentId: number, courseId: number): Observable<ApiEnrollment> {
    return this.http.post<ApiEnrollment>(this.enrollmentUrl, { studentId, courseId });
  }

  /** GET /api/courses/{courseId}/certificate?studentId={studentId} — returns PDF blob */
  downloadCertificate(courseId: number, studentId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${courseId}/certificate?studentId=${studentId}`, { responseType: 'blob' });
  }

  // ==================== PAYMENTS ====================

  /** POST /api/payments/create-checkout-session */
  createCheckoutSession(request: CheckoutSessionRequest): Observable<CheckoutSessionResponse> {
    return this.http.post<CheckoutSessionResponse>('/api/payments/create-checkout-session', request);
  }

  /** POST /api/payments/confirm-checkout-session */
  confirmCheckoutSession(request: CheckoutConfirmRequest): Observable<CheckoutConfirmResponse> {
    return this.http.post<CheckoutConfirmResponse>('/api/payments/confirm-checkout-session', request);
  }

  /** GET /api/payments/config */
  getStripePublishableKey(): Observable<{ publishableKey: string }> {
    return this.http.get<{ publishableKey: string }>('/api/payments/config');
  }

  // ==================== LEARNING PATHS ====================

  generateLearningPath(studentId: number, goal: string): Observable<LearningPath> {
    return this.http.post<LearningPath>('/api/learning-paths/generate', { studentId, goal });
  }

  saveLearningPath(path: LearningPath, whyIncludedJson?: string): Observable<LearningPath> {
    return this.http.post<LearningPath>('/api/learning-paths/save', {
      studentId: path.studentId,
      goal: path.goal,
      generatedTitle: path.generatedTitle,
      generatedDescription: path.generatedDescription,
      courseIds: path.courses.map(c => c.id),
      totalPrice: path.totalPrice,
      totalDurationMinutes: path.totalDurationMinutes,
      whyIncludedJson: whyIncludedJson ?? null
    });
  }

  /** GET /api/students/{studentId}/dashboard */
  getStudentDashboard(studentId: number): Observable<StudentDashboard> {
    return this.http.get<StudentDashboard>(`/api/students/${studentId}/dashboard`);
  }

  getSavedLearningPaths(studentId: number): Observable<LearningPath[]> {
    return this.http.get<LearningPath[]>(`/api/learning-paths/student/${studentId}`);
  }

  deleteLearningPath(pathId: number): Observable<void> {
    return this.http.delete<void>(`/api/learning-paths/${pathId}`);
  }

  trackTimeSpent(courseId: number, lessonId: number, studentId: number, seconds: number): Observable<void> {
    return this.http.patch<void>(
      `/api/courses/${courseId}/lessons/${lessonId}/time?studentId=${studentId}&seconds=${seconds}`,
      {}
    );
  }

  enrollInPath(pathId: number, studentId: number): Observable<any> {
    return this.http.post<any>(
      `/api/learning-paths/${pathId}/enroll?studentId=${studentId}`,
      {}
    );
  }

  /** GET /api/ml/recommendations?category=&level=&topN= */
  getMLRecommendations(category: string, level: string, topN: number = 5): Observable<MlRecommendationsResponse> {
    return this.http.get<MlRecommendationsResponse>(
      `/api/ml/recommendations?category=${category}&level=${level}&topN=${topN}`
    ).pipe(
      catchError(() => of({ recommendations: [] as MlRecommendation[], basedOn: { category, level } }))
    );
  }

  /** GET /api/ml/recommendations?studentId=&topN= — backend derives category/level from all enrollments */
  getRecommendationsByStudent(studentId: number, topN: number = 5): Observable<MlRecommendationsResponse> {
    return this.http.get<MlRecommendationsResponse>(
      `/api/ml/recommendations?studentId=${studentId}&topN=${topN}`
    ).pipe(
      catchError(() => of({ recommendations: [] as MlRecommendation[], basedOn: { category: 'PROGRAMMING', level: 'BEGINNER' } }))
    );
  }

  /** POST /api/courses/search/ai */
  aiSearch(query: string, page: number = 0, size: number = 12): Observable<AiSearchResult> {
    return this.http.post<AiSearchResult>(
      `/api/courses/search/ai?page=${page}&size=${size}`,
      { query }
    );
  }

  /** GET /api/enrollments/check?studentId=&courseId= */
  checkEnrollment(studentId: number, courseId: number): Observable<EnrollmentCheckResponse> {
    return this.http.get<EnrollmentCheckResponse>(
      `/api/enrollments/check?studentId=${studentId}&courseId=${courseId}`
    );
  }

  /** GET /api/courses/verify/{certificateId} */
  verifyCertificate(certificateId: string): Observable<CertificateVerification> {
    return this.http.get<CertificateVerification>(`/api/courses/verify/${certificateId}`);
  }

  /** GET /api/ml/student/{studentId}/conversion */
  getConversionInsight(studentId: number): Observable<any> {
    return this.http.get<any>(`/api/ml/student/${studentId}/conversion`);
  }

  /** POST /api/ml/courses/quality-batch — body: courseIds[] */
  getCourseQualityBatch(courseIds: number[]): Observable<{[key: number]: MlQualityResult}> {
    return this.http.post<{[key: number]: MlQualityResult}>(
      '/api/ml/courses/quality-batch',
      courseIds
    ).pipe(catchError(() => of({})));
  }
}
