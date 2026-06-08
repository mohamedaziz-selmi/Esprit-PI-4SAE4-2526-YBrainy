import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApplicationGeneratorStoreService } from '../../shared/stores/application-generator-store.service';
import { AtsScoreResult, GenerateApplicationResponse } from '../../shared/models/application-generator.models';
import { applyBackofficeBodyClass } from '../utils/backoffice-body-class';
import { computeAtsScore, downloadSimplePdf } from './ai-application.utils';
import { environment } from '@env/environment';
import { AnimatedButtonComponent } from './components/animated-button.component';
import { BackofficeDashboardSidebarComponent } from '../shared/backoffice-dashboard-sidebar.component';
import { CvUploadCardComponent } from './components/cv-upload-card.component';
import { JobDescriptionCardComponent } from './components/job-description-card.component';
import { LoaderComponent } from './components/loader.component';
import { ResultCardComponent } from './components/result-card.component';
import { ResultTab, ResultTabsComponent } from './components/result-tabs.component';

@Component({
  selector: 'app-backoffice-ai-application',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    BackofficeDashboardSidebarComponent,
    AnimatedButtonComponent,
    CvUploadCardComponent,
    JobDescriptionCardComponent,
    LoaderComponent,
    ResultTabsComponent,
    ResultCardComponent,
  ],
  templateUrl: './backoffice-ai-application.component.html',
  styleUrl: './backoffice-ai-application.component.css',
  host: { style: 'display:block' },
})
export class BackofficeAiApplicationComponent implements OnInit, OnDestroy {
  private static readonly MAX_INPUT_CHARS = 1_000_000;

  cvText = '';
  fileName: string | null = null;
  jobDescription = '';

  optimizedCV = '';
  coverLetter = '';

  activeTab: ResultTab = 'optimizedCV';
  atsResult: AtsScoreResult | null = null;

  loading = false;
  errorMessage = '';
  showSuccess = false;

  readonly steps = ['Upload CV', 'Match Job', 'Results'];

  private readonly sub = new Subscription();
  private restoreBodyClass: (() => void) | null = null;

  constructor(private generatorStore: ApplicationGeneratorStoreService) {}

  ngOnInit(): void {
    this.restoreBodyClass = applyBackofficeBodyClass();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.restoreBodyClass?.();
    this.restoreBodyClass = null;
  }

  get hasResults(): boolean {
    return !!this.optimizedCV.trim() || !!this.coverLetter.trim();
  }

  get currentStep(): number {
    if (this.hasResults) return 3;
    if (this.cvText.trim() && this.jobDescription.trim()) return 2;
    return 1;
  }

  get activeContent(): string {
    return this.activeTab === 'optimizedCV' ? this.optimizedCV : this.coverLetter;
  }

  get activeTitle(): string {
    return this.activeTab === 'optimizedCV' ? 'Optimized CV' : 'Cover Letter';
  }

  onCvChanged(payload: { text: string; fileName: string | null }): void {
    this.cvText = payload.text;
    this.fileName = payload.fileName;
  }

  onResultChanged(content: string): void {
    if (this.activeTab === 'optimizedCV') {
      this.optimizedCV = content;
      this.atsResult = computeAtsScore(this.jobDescription, this.optimizedCV);
      return;
    }
    this.coverLetter = content;
  }

  setActiveTab(tab: ResultTab): void {
    this.activeTab = tab;
  }

  generate(): void {
    if (!this.cvText.trim() || !this.jobDescription.trim()) {
      this.errorMessage = 'Please provide both CV text and job description first.';
      return;
    }
    if (
      this.cvText.length > BackofficeAiApplicationComponent.MAX_INPUT_CHARS ||
      this.jobDescription.length > BackofficeAiApplicationComponent.MAX_INPUT_CHARS
    ) {
      this.errorMessage = `Input too long. Keep CV and job description below ${BackofficeAiApplicationComponent.MAX_INPUT_CHARS} characters each.`;
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.showSuccess = false;

    this.sub.add(
      this.generatorStore
        .generate({
          cv: this.cvText.trim(),
          jobDescription: this.jobDescription.trim(),
        })
        .subscribe({
          next: (response: GenerateApplicationResponse) => {
            this.loading = false;
            this.optimizedCV = response.optimizedCV;
            this.coverLetter = response.coverLetter;
            this.activeTab = 'optimizedCV';
            this.atsResult = computeAtsScore(this.jobDescription, this.optimizedCV);
            this.showSuccess = true;
            window.setTimeout(() => (this.showSuccess = false), 2200);
          },
          error: (error: unknown) => {
            this.loading = false;
            this.errorMessage = this.buildErrorMessage(error);
          },
        })
    );
  }

  downloadPdf(): void {
    const filename = this.activeTab === 'optimizedCV' ? 'optimized-cv.pdf' : 'cover-letter.pdf';
    downloadSimplePdf(filename, this.activeTitle, this.activeContent);
  }

  private buildErrorMessage(error: unknown): string {
    const fallback = 'Generation failed. Please retry in a moment.';
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    const backendMessage =
      typeof error.error === 'object' && error.error && typeof (error.error as { message?: unknown }).message === 'string'
        ? ((error.error as { message: string }).message as string)
        : '';

    if (backendMessage.trim()) {
      const details =
        typeof error.error === 'object' && error.error && Array.isArray((error.error as { details?: unknown }).details)
          ? (((error.error as { details: unknown[] }).details as unknown[]).filter(
              (item): item is string => typeof item === 'string'
            ) as string[])
          : [];
      return details.length ? `${backendMessage}: ${details.join(', ')}` : backendMessage;
    }

    if (error.status === 0) {
      return `Partner backend unavailable. Start the gateway and job-offer-service for ${environment.partnerApiBaseUrl}.`;
    }

    return fallback;
  }
}
