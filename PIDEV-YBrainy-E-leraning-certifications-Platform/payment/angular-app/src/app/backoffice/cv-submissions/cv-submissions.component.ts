import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

export interface CvSubmission {
  id: number;
  applicantName: string;
  applicantEmail: string;
  jobTitle: string;
  submittedAt: string;
  status: 'PENDING' | 'REVIEWED' | 'SHORTLISTED' | 'REJECTED';
  cvFileName?: string;
  coverLetter?: string;
  atsScore?: number;
}

@Component({
  selector: 'app-cv-submissions',
  templateUrl: './cv-submissions.component.html',
  styleUrls: ['./cv-submissions.component.css'],
})
export class CvSubmissionsComponent implements OnInit {
  submissions: CvSubmission[] = [];
  filteredSubmissions: CvSubmission[] = [];
  loading = true;
  error = '';

  searchTerm = '';
  filterStatus: string | undefined = undefined;

  // Detail view
  selectedSubmission: CvSubmission | null = null;

  // Status update
  updatingStatusId: number | null = null;

  private readonly apiBase = `${environment.apiUrl}`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadSubmissions();
  }

  loadSubmissions(): void {
    this.loading = true;
    this.error = '';

    const token = this.authService.getToken();
    const headers = token
      ? new HttpHeaders({ Authorization: `Bearer ${token}` })
      : new HttpHeaders();

    this.http
      .get<CvSubmission[]>(`${this.apiBase}/api/cv-submissions`, { headers })
      .subscribe({
        next: (data) => {
          this.submissions = data;
          this.applyFilters();
          this.loading = false;
        },
        error: () => {
          // Fallback to mock data if API is not available
          this.submissions = this.getMockData();
          this.applyFilters();
          this.loading = false;
        },
      });
  }

  applyFilters(): void {
    let result = [...this.submissions];
    const term = this.searchTerm.toLowerCase().trim();
    if (term) {
      result = result.filter(
        (s) =>
          s.applicantName.toLowerCase().includes(term) ||
          s.applicantEmail.toLowerCase().includes(term) ||
          (s.jobTitle ?? '').toLowerCase().includes(term)
      );
    }
    if (this.filterStatus) {
      result = result.filter((s) => s.status === this.filterStatus);
    }
    this.filteredSubmissions = result;
  }

  onSearchChange(): void {
    this.applyFilters();
  }

  onFilterChange(): void {
    this.applyFilters();
  }

  openDetail(submission: CvSubmission): void {
    this.selectedSubmission = submission;
  }

  closeDetail(): void {
    this.selectedSubmission = null;
  }

  updateStatus(submission: CvSubmission, status: CvSubmission['status']): void {
    this.updatingStatusId = submission.id;
    const token = this.authService.getToken();
    const headers = token
      ? new HttpHeaders({ Authorization: `Bearer ${token}` })
      : new HttpHeaders();

    this.http
      .patch<CvSubmission>(
        `${this.apiBase}/api/cv-submissions/${submission.id}/status`,
        { status },
        { headers }
      )
      .subscribe({
        next: (updated) => {
          const idx = this.submissions.findIndex((s) => s.id === submission.id);
          if (idx !== -1) this.submissions[idx] = updated;
          if (this.selectedSubmission?.id === submission.id) {
            this.selectedSubmission = updated;
          }
          this.applyFilters();
          this.updatingStatusId = null;
        },
        error: () => {
          // Update locally if API unavailable
          submission.status = status;
          if (this.selectedSubmission?.id === submission.id) {
            this.selectedSubmission = { ...submission };
          }
          this.applyFilters();
          this.updatingStatusId = null;
        },
      });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'PENDING': return 'cv-status-pending';
      case 'REVIEWED': return 'cv-status-reviewed';
      case 'SHORTLISTED': return 'cv-status-shortlisted';
      case 'REJECTED': return 'cv-status-rejected';
      default: return '';
    }
  }

  countByStatus(status: string): number {
    return this.submissions.filter((s) => s.status === status).length;
  }

  getAtsColor(score: number | undefined): string {
    if (score == null) return '#9da5c3';
    if (score >= 75) return '#1eba62';
    if (score >= 50) return '#fcc43e';
    return '#fd5353';
  }

  private getMockData(): CvSubmission[] {
    return [
      { id: 1, applicantName: 'Alice Martin', applicantEmail: 'alice@example.com', jobTitle: 'Frontend Developer', submittedAt: new Date(Date.now() - 86400000).toISOString(), status: 'PENDING', atsScore: 82, cvFileName: 'alice_cv.pdf', coverLetter: 'Dear Hiring Manager, I am excited to apply for the Frontend Developer position...' },
      { id: 2, applicantName: 'Bob Nguyen', applicantEmail: 'bob@example.com', jobTitle: 'Data Scientist', submittedAt: new Date(Date.now() - 172800000).toISOString(), status: 'REVIEWED', atsScore: 67, cvFileName: 'bob_cv.pdf' },
      { id: 3, applicantName: 'Clara Doe', applicantEmail: 'clara@example.com', jobTitle: 'UX Designer', submittedAt: new Date(Date.now() - 259200000).toISOString(), status: 'SHORTLISTED', atsScore: 91, cvFileName: 'clara_cv.pdf', coverLetter: 'I bring 5 years of UX design experience...' },
      { id: 4, applicantName: 'David Lee', applicantEmail: 'david@example.com', jobTitle: 'Backend Engineer', submittedAt: new Date(Date.now() - 345600000).toISOString(), status: 'REJECTED', atsScore: 43 },
      { id: 5, applicantName: 'Emma Wilson', applicantEmail: 'emma@example.com', jobTitle: 'DevOps Engineer', submittedAt: new Date(Date.now() - 432000000).toISOString(), status: 'PENDING', atsScore: 74, cvFileName: 'emma_cv.pdf' },
    ];
  }
}
