import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { JobApplication, JobApplicationStatus } from '../models/job-application.models';

interface BackendJobApplicationRequest {
  applicantName: string;
  applicantEmail: string;
  message: string | null;
  cvDataUrl: string | null;
}

interface BackendJobApplicationResponse {
  id: string;
  offerId: string;
  applicantName: string;
  applicantEmail: string;
  message: string | null;
  cvDataUrl: string | null;
  status: JobApplicationStatus | null;
  reviewerNotes: string | null;
  createdAt: string;
}

interface BackendJobApplicationUpdateRequest {
  status: JobApplicationStatus;
  reviewerNotes: string | null;
}

@Injectable({ providedIn: 'root' })
export class JobApplicationStoreService {
  private readonly apiBaseUrl = '/api';

  constructor(private http: HttpClient) {}

  listAll(): Observable<JobApplication[]> {
    return this.http
      .get<BackendJobApplicationResponse[]>(`${this.apiBaseUrl}/applications`)
      .pipe(map((items) => items.map((a) => this.fromBackend(a))));
  }

  listByOffer(offerId: string): Observable<JobApplication[]> {
    return this.http
      .get<BackendJobApplicationResponse[]>(`${this.apiBaseUrl}/offers/${offerId}/applications`)
      .pipe(map((items) => items.map((a) => this.fromBackend(a))));
  }

  hasApplied(offerId: string, email: string): Observable<boolean> {
    const normalized = email.trim().toLowerCase();
    return this.listByOffer(offerId).pipe(
      map((items) => items.some((a) => a.applicantEmail.trim().toLowerCase() === normalized))
    );
  }

  apply(input: Omit<JobApplication, 'id' | 'createdAt' | 'status' | 'reviewerNotes'>): Observable<JobApplication> {
    const body: BackendJobApplicationRequest = {
      applicantName: input.applicantName.trim(),
      applicantEmail: input.applicantEmail.trim(),
      message: input.message?.trim() || null,
      cvDataUrl: input.cvDataUrl || null,
    };
    return this.http
      .post<BackendJobApplicationResponse>(`${this.apiBaseUrl}/offers/${input.offerId}/applications`, body)
      .pipe(map((saved) => this.fromBackend(saved)));
  }

  updateReview(
    applicationId: string,
    input: { status: JobApplicationStatus; reviewerNotes?: string | null }
  ): Observable<JobApplication> {
    const body: BackendJobApplicationUpdateRequest = {
      status: input.status,
      reviewerNotes: input.reviewerNotes?.trim() || null,
    };
    return this.http
      .put<BackendJobApplicationResponse>(`${this.apiBaseUrl}/applications/${applicationId}`, body)
      .pipe(map((saved) => this.fromBackend(saved)));
  }

  delete(applicationId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/applications/${applicationId}`);
  }

  private fromBackend(item: BackendJobApplicationResponse): JobApplication {
    return {
      id: item.id,
      offerId: item.offerId,
      applicantName: item.applicantName,
      applicantEmail: item.applicantEmail,
      message: item.message ?? undefined,
      cvDataUrl: item.cvDataUrl ?? undefined,
      status: item.status ?? 'PENDING',
      reviewerNotes: item.reviewerNotes ?? undefined,
      createdAt: item.createdAt,
    };
  }
}


