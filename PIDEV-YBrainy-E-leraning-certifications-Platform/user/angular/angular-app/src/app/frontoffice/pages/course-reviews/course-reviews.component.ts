import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseApiService, SpringPage } from '../../services/course-api.service';
import { ApiReview, RatingDistribution } from '../../models/course.models';
import { UserSessionService } from '../../../tracking/user-session.service';

@Component({
  selector: 'app-course-reviews',
  standalone: false,
  templateUrl: './course-reviews.component.html',
  styleUrls: ['./course-reviews.component.css'],
})
export class CourseReviewsComponent implements OnInit {
  courseId = 0;

  get studentId(): number { return this.userSession.get()?.userId ?? 0; }
  get isStudent(): boolean { return (this.userSession.get()?.role ?? 'STUDENT') === 'STUDENT'; }
  get alreadyReviewed(): boolean {
    return this.page.content.some(r => r.studentId === this.studentId);
  }

  editingReviewId: number | null = null;
  editRating: number = 0;
  editComment: string = '';
  deleting: number | null = null;

  isEnrolled = false;
  enrollmentChecked = false;

  page: SpringPage<ApiReview> = { content: [] };
  ratingStats: RatingDistribution | null = null;
  newRating = 0;
  newComment = '';
  error = '';
  success = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: CourseApiService,
    private userSession: UserSessionService
  ) {}

  ngOnInit(): void {
    this.courseId = +(this.route.snapshot.paramMap.get('courseId') ?? '0');
    if (!this.isStudent) {
      this.isEnrolled = true;
      this.enrollmentChecked = true;
      this.loadReviews();
      return;
    }
    try {
      const studentId = this.requireAuth();
      this.api.getStudentEnrollments(studentId).subscribe({
        next: (enrollments) => {
          this.isEnrolled = enrollments.some(e => e.courseId === this.courseId);
          this.enrollmentChecked = true;
          if (this.isEnrolled) this.loadReviews();
        },
        error: () => { this.isEnrolled = false; this.enrollmentChecked = true; },
      });
    } catch {
      this.isEnrolled = false;
      this.enrollmentChecked = true;
    }
  }

  loadReviews(): void {
    this.api.getReviews(this.courseId).subscribe({
      next: (p) => { this.page = p; },
      error: () => {},
    });
    this.api.getRatingStats(this.courseId).subscribe({
      next: (s) => { this.ratingStats = s; },
      error: () => {},
    });
  }

  getBarWidth(count: number): string {
    if (!this.ratingStats || this.ratingStats.totalReviews === 0) return '0%';
    return Math.round((count / this.ratingStats.totalReviews) * 100) + '%';
  }

  setRating(n: number): void {
    this.newRating = n;
  }

  onCommentChange(event: Event): void {
    this.newComment = (event.target as HTMLTextAreaElement).value;
  }

  submitReview(): void {
    if (this.newRating < 1) { this.error = 'Please select a rating.'; return; }
    if (!this.newComment || this.newComment.trim().length < 5) {
      this.error = 'Comment must be at least 5 characters.';
      return;
    }
    try {
      const studentId = this.requireAuth();
      this.error = '';
      this.success = '';
      this.api.addReview(this.courseId, studentId, this.newRating, this.newComment).subscribe({
        next: () => {
          this.success = 'Review submitted!';
          this.newRating = 0;
          this.newComment = '';
          this.loadReviews();
        },
        error: (err: any) => {
          this.error = err.status === 409
            ? 'You have already reviewed this course.'
            : 'Could not submit review.';
        },
      });
    } catch {
      return; // redirected to login
    }
  }

  startEdit(review: any): void {
    this.editingReviewId = review.id;
    this.editRating = review.rating;
    this.editComment = review.comment;
  }

  cancelEdit(): void {
    this.editingReviewId = null;
    this.editRating = 0;
    this.editComment = '';
  }

  saveEdit(reviewId: number): void {
    try {
      const studentId = this.requireAuth();

      if (!this.editRating || this.editRating < 1 || this.editRating > 5) {
        alert('Please select a rating between 1 and 5');
        return;
      }

      if (!this.editComment.trim()) {
        alert('Please enter a comment');
        return;
      }

      this.api.updateReview(this.courseId, reviewId, studentId, this.editRating, this.editComment)
        .subscribe({
          next: () => {
            this.loadReviews();
            this.cancelEdit();
          },
          error: (err) => {
            console.error('Update review error:', err);
            alert(err.error?.message || 'Failed to update review');
          }
        });
    } catch {
      return;
    }
  }

  confirmDelete(reviewId: number): void {
    if (!confirm('Are you sure you want to delete this review?')) {
      return;
    }

    try {
      const studentId = this.requireAuth();
      this.deleting = reviewId;

      this.api.deleteReview(this.courseId, reviewId, studentId)
        .subscribe({
          next: () => {
            this.loadReviews();
            this.deleting = null;
          },
          error: (err) => {
            console.error('Delete review error:', err);
            alert(err.error?.message || 'Failed to delete review');
            this.deleting = null;
          }
        });
    } catch {
      this.deleting = null;
    }
  }

  setEditRating(rating: number): void {
    this.editRating = rating;
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }

  back(): void {
    this.router.navigate(['/courses', this.courseId]);
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
