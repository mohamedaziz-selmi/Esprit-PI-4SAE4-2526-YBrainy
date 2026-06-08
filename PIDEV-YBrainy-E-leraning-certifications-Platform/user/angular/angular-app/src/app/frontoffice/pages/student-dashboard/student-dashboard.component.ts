import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Router } from '@angular/router';
import { ConversionInsight, EnrolledCourse, StudentDashboard } from '../../models/course.models';
import { CourseApiService } from '../../services/course-api.service';
import { UserSessionService } from '../../../tracking/user-session.service';
import { courseMediaUrl } from '../../utils/course-media-url';

@Component({
  selector: 'app-student-dashboard',
  standalone: false,
  templateUrl: './student-dashboard.component.html',
  styleUrls: ['./student-dashboard.component.css']
})
export class StudentDashboardComponent implements OnInit, AfterViewInit {
  dashboard: StudentDashboard | null = null;
  loading = true;
  activeTab: 'all' | 'active' | 'completed' = 'all';
  conversionInsight: ConversionInsight | null = null;
  conversionLoading = false;
  certDownloading = false;
  certError = '';

  constructor(
    private api: CourseApiService,
    private userSession: UserSessionService,
    private router: Router
  ) {}

  get studentId(): number { return this.userSession.get()?.userId ?? 0; }

  ngOnInit(): void {
    try {
      const studentId = this.requireAuth();
      this.api.getStudentDashboard(studentId).subscribe({
        next: (d) => {
          this.dashboard = d;
          this.loading = false;
          this.conversionLoading = true;
          this.api.getConversionInsight(studentId).subscribe({
            next: (data) => {
              this.conversionInsight = data;
              this.conversionLoading = false;
            },
            error: () => {
              this.conversionLoading = false;
            }
          });
        },
        error: () => { this.loading = false; }
      });
    } catch {
      this.loading = false;
      return; // redirected to login
    }
  }

  get filteredCourses(): EnrolledCourse[] {
    if (!this.dashboard) return [];
    if (this.activeTab === 'active')
      return this.dashboard.enrolledCourses.filter(c => c.enrollmentStatus === 'ACTIVE');
    if (this.activeTab === 'completed')
      return this.dashboard.enrolledCourses.filter(c => c.enrollmentStatus === 'COMPLETED');
    return this.dashboard.enrolledCourses;
  }

  goToCourse(courseId: number): void {
    this.router.navigate(['/courses', courseId]);
  }

  goToProgress(courseId: number): void {
    this.router.navigate(['/courses', courseId, 'progress']);
  }

  downloadCertificate(course: EnrolledCourse): void {
    if (!course.certificateId) return;
    try {
      const studentId = this.requireAuth();
      this.certDownloading = true;
      this.certError = '';
      this.api.downloadCertificate(course.courseId, studentId).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `YBrainy-Certificate-${course.courseTitle}.pdf`;
          a.click();
          URL.revokeObjectURL(url);
          this.certDownloading = false;
        },
        error: () => {
          this.certError = 'Could not download certificate. Please try again.';
          this.certDownloading = false;
        }
      });
    } catch {
      this.certDownloading = false;
    }
  }

  getThumbnailUrl(course: EnrolledCourse): string {
    return courseMediaUrl(course.thumbnailUrl);
  }

  private requireAuth(): number {
    const userId = this.userSession.get()?.userId;
    if (!userId || userId <= 0) {
      this.router.navigate(['/login']);
      throw new Error('Authentication required');
    }
    return userId;
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initAnimations(), 300);
  }

  initAnimations(): void {
    // Wait for Angular to fully render data-bound values
    setTimeout(() => {

      // Counter animation for stat cards
      document.querySelectorAll('.scard-val').forEach(el => {
        const text = el.textContent?.trim() || '0';
        const target = parseInt(text, 10);
        if (isNaN(target) || target === 0) return;
        let current = 0;
        const steps = 40;
        const increment = target / steps;
        const timer = setInterval(() => {
          current = Math.min(current + increment, target);
          el.textContent = Math.floor(current).toString();
          if (current >= target) clearInterval(timer);
        }, 20);
      });

      // Ring counter animation
      const ringNum = document.querySelector('.ring-num');
      if (ringNum) {
        const text = ringNum.textContent?.trim() || '0';
        const target = parseInt(text, 10);
        if (!isNaN(target) && target > 0) {
          let current = 0;
          const steps = 50;
          const increment = target / steps;
          const timer = setInterval(() => {
            current = Math.min(current + increment, target);
            ringNum.textContent = Math.floor(current).toString();
            if (current >= target) clearInterval(timer);
          }, 20);
        }
      }

      // Stat bar fills with stagger
      document.querySelectorAll('.scard-bar-fill').forEach((el: any, i) => {
        setTimeout(() => { el.style.width = '65%'; }, i * 100);
      });

      // 3D tilt
      document.querySelectorAll('.ccard').forEach((card: any) => {
        card.addEventListener('mousemove', (e: MouseEvent) => {
          const rect = card.getBoundingClientRect();
          const x = e.clientX - rect.left;
          const y = e.clientY - rect.top;
          const cx = rect.width / 2;
          const cy = rect.height / 2;
          const rotX = ((y - cy) / cy) * -5;
          const rotY = ((x - cx) / cx) * 5;
          card.style.transform = `perspective(800px) rotateX(${rotX}deg) rotateY(${rotY}deg) translateZ(6px)`;
          card.style.transition = 'none';
        });
        card.addEventListener('mouseleave', () => {
          card.style.transition = 'transform 0.4s ease';
          card.style.transform = 'perspective(800px) rotateX(0) rotateY(0) translateZ(0)';
        });
      });

    }, 600);
  }

  onCardTilt(event: MouseEvent): void {}
  onCardReset(event: MouseEvent): void {}
}
