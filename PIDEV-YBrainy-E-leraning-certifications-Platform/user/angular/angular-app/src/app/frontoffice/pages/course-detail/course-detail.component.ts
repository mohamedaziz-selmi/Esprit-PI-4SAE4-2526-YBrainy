import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SafeResourceUrl } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { CourseDetail, CheckoutSessionRequest } from '../../models/course.models';
import { CourseStoreService } from '../../services/course-store.service';
import { CourseApiService } from '../../services/course-api.service';
import { AiReaderService } from '../../services/ai-reader.service';
import { UserSessionService } from '../../../tracking/user-session.service';

@Component({
  selector: 'app-course-detail',
  standalone: false,
  templateUrl: './course-detail.component.html',
  styleUrls: ['./course-detail.component.css'],
  host: { style: 'display:block' },
})
export class CourseDetailComponent implements OnInit, OnDestroy {
  course: CourseDetail | null = null;
  courseId = '';
  loading = true;

  enrolled = false;
  enrolling = false;
  enrollError = '';
  enrollment: any = null;
  readerOpen = false;
  readerLoading = false;
  readerFrameUrl: SafeResourceUrl | null = null;
  readerError = '';
  readerSpeakerSource = '';

  private readonly sub = new Subscription();
  private handledCheckoutSessionId = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private store: CourseStoreService,
    private api: CourseApiService,
    private aiReader: AiReaderService,
    private userSession: UserSessionService
  ) {}

  get studentId(): number { return this.userSession.get()?.userId ?? 0; }
  private get isStudent(): boolean { return (this.userSession.get()?.role ?? 'STUDENT') === 'STUDENT'; }

  ngOnInit(): void {
    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        this.courseId = pm.get('courseId') ?? '';
        const id = this.courseId ? +this.courseId : NaN;
        this.enrolled = false;
        this.enrollError = '';
        this.closeReader();
        if (!isNaN(id)) {
          this.checkEnrollment(id);
          this.sub.add(
            this.store.getCourseById(id).subscribe({
              next: (detail) => { this.course = detail; this.loading = false; },
              error: () => { this.course = null; this.loading = false; },
            })
          );
        } else {
          this.course = null;
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

    // Handle redirect back from Stripe checkout
    this.sub.add(
      this.route.queryParams.subscribe((params) => {
        if (params['payment'] === 'success' && this.courseId && !this.enrolled) {
          const id = +this.courseId;
          const sessionId = String(params['session_id'] ?? '');
          if (!isNaN(id)) {
            try {
              this.requireAuth();
              if (!sessionId || this.handledCheckoutSessionId === sessionId) {
                this.checkEnrollment(id);
                return;
              }

              this.handledCheckoutSessionId = sessionId;
              this.enrolling = true;
              this.enrollError = '';
              this.api.confirmCheckoutSession({ sessionId }).subscribe({
                next: (response) => {
                  this.enrollment = response.enrollments.find(e => e.courseId === id) ?? response.enrollments[0] ?? null;
                  this.enrolled = !!this.enrollment;
                  this.enrolling = false;
                  this.router.navigate([], {
                    relativeTo: this.route,
                    queryParams: {},
                    replaceUrl: true,
                  });
                },
                error: () => {
                  this.enrolling = false;
                  this.enrollError = 'Payment succeeded, but enrollment confirmation failed. Please refresh or try again.';
                  this.checkEnrollment(id);
                },
              });
            } catch {
              // not logged in, ignore Stripe redirect
            }
          }
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.store.clearSelectedCourse();
    this.sub.unsubscribe();
  }

  checkEnrollment(courseId: number): void {
    try {
      const studentId = this.requireAuth();
      this.api.getStudentEnrollments(studentId).subscribe({
        next: (enrollments) => {
          this.enrollment = enrollments.find(e => e.courseId === courseId) ?? null;
          this.enrolled = !!this.enrollment;
        },
        error: () => {
          this.enrolled = false;
          this.enrollment = null;
        },
      });
    } catch {
      this.enrolled = false;
      this.enrollment = null;
    }
  }

  enroll(): void {
    if (this.enrolling || !this.courseId) return;
    try {
      const studentId = this.requireAuth();
      this.enrolling = true;
      this.enrollError = '';
      this.api.enroll(studentId, +this.courseId).subscribe({
        next: () => { this.enrolled = true; this.enrolling = false; },
        error: () => {
          this.enrollError = 'Enrollment failed. Please try again.';
          this.enrolling = false;
        },
      });
    } catch {
      return; // redirected to login
    }
  }

  enrollOrPay(): void {
    if (this.enrolling || !this.courseId || !this.course) return;
    if (!this.course.price || this.course.price === 0) {
      this.enroll();
    } else {
      try {
        const studentId = this.requireAuth();
        this.enrolling = true;
        this.enrollError = '';
        const request: CheckoutSessionRequest = {
          courseId: +this.courseId,
          studentId,
          courseTitle: this.course.title,
          price: this.course.price,
        };
        this.api.createCheckoutSession(request).subscribe({
          next: (response) => { window.location.href = response.checkoutUrl; },
          error: () => {
            this.enrollError = 'Payment setup failed. Please try again.';
            this.enrolling = false;
          },
        });
      } catch {
        return; // redirected to login
      }
    }
  }

  backToCourses(): void {
    this.router.navigateByUrl('/courses');
  }

  viewLessons(): void {
    if (!this.courseId) return;
    this.router.navigate(['/courses', this.courseId, 'lessons']);
  }

  goToProgress(): void {
    if (!this.courseId) return;
    this.router.navigate(['/courses', this.courseId, 'progress']);
  }

  goToQuiz(): void {
    if (!this.courseId) return;
    this.router.navigate(['/courses', this.courseId, 'quiz']);
  }

  toggleReader(): void {
    if (this.readerOpen) {
      this.closeReader();
      return;
    }
    this.openReader();
  }

  openReader(): void {
    const id = this.courseId ? +this.courseId : NaN;
    if (isNaN(id) || this.readerLoading) return;

    this.readerLoading = true;
    this.readerError = '';
    this.sub.add(
      this.aiReader.getCourseReaderFrame(id).subscribe({
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

  goToReviews(): void {
    if (!this.courseId) return;
    this.router.navigate(['/courses', this.courseId, 'reviews']);
  }

  formatDate(value: string | undefined): string {
    if (!value) return '--';
    const d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  }

  formatPrice(value: number | undefined | null): string {
    const n = Number(value);
    if (!isFinite(n)) return '--';
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(n);
    } catch {
      return `${n.toFixed(2)} $`;
    }
  }

  private requireAuth(): number {
    const userId = this.userSession.get()?.userId;
    if (!userId || userId <= 0) {
      this.router.navigate(['/login']);
      throw new Error('Authentication required');
    }
    return userId;
  }
}
