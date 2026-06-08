import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { JobApplication, JobApplicationStatus } from '../../shared/models/job-application.models';
import { JobOffer } from '../../shared/models/job-offer.models';
import { Partnership } from '../../shared/models/partnership.models';
import { JobApplicationStoreService } from '../../shared/stores/job-application-store.service';
import { JobOfferStoreService } from '../../shared/stores/job-offer-store.service';
import { PartnershipStoreService } from '../../shared/stores/partnership-store.service';
import { BackofficeDashboardSidebarComponent } from '../shared/backoffice-dashboard-sidebar.component';
import { applyBackofficeBodyClass } from '../utils/backoffice-body-class';

type CandidateMatchView = JobApplication & {
  offer?: JobOffer;
  partner?: Partnership;
  compatibilityScore: number;
  matchedKeywords: string[];
  missingKeywords: string[];
  keywordCount: number;
  matchedCount: number;
  cvReadable: boolean;
};

type ScoreSummary = {
  average: number;
  max: number;
  min: number;
  aboveThreshold: number;
  withoutCvText: number;
};

const APPLICATION_STATUSES: JobApplicationStatus[] = ['PENDING', 'REVIEWED', 'SHORTLISTED', 'ACCEPTED', 'REJECTED'];
const STOP_WORDS = new Set([
  'about', 'above', 'after', 'again', 'against', 'along', 'among', 'and', 'are', 'because', 'been',
  'before', 'being', 'below', 'between', 'both', 'could', 'doing', 'each', 'from', 'have', 'into',
  'just', 'more', 'most', 'other', 'over', 'same', 'some', 'such', 'that', 'their', 'there', 'these',
  'they', 'this', 'those', 'under', 'very', 'what', 'when', 'where', 'which', 'while', 'with', 'your',
  'pour', 'avec', 'dans', 'comme', 'plus', 'sans', 'vous', 'nous', 'leur', 'elle', 'elles', 'afin',
  'aussi', 'cela', 'cette', 'entre', 'selon', 'dont', 'tout', 'tous', 'toute', 'toutes',
]);

@Component({
  selector: 'app-backoffice-candidate-evaluation',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackofficeDashboardSidebarComponent],
  templateUrl: './backoffice-candidate-evaluation.component.html',
  styleUrl: './backoffice-candidate-evaluation.component.css',
  host: { style: 'display:block' },
})
export class BackofficeCandidateEvaluationComponent implements OnInit, OnDestroy {
  loading = true;
  loadingError: string | null = null;
  evaluationError: string | null = null;
  evaluationInfo: string | null = null;
  offersLoadError: string | null = null;
  partnersLoadError: string | null = null;

  selectedOfferId = '';
  topCount = 5;
  minCompatibility = 70;
  autoStatusEnabled = false;
  isAnalyzing = false;
  isApplyingAutoStatus = false;

  applications: JobApplication[] = [];
  offers: JobOffer[] = [];
  partners: Partnership[] = [];
  analyzedCandidates: CandidateMatchView[] = [];
  topCandidates: CandidateMatchView[] = [];
  scoreSummary: ScoreSummary = {
    average: 0,
    max: 0,
    min: 0,
    aboveThreshold: 0,
    withoutCvText: 0,
  };

  readonly applicationStatuses = APPLICATION_STATUSES;

  private readonly sub = new Subscription();
  private readonly updatingStatusIds = new Set<string>();
  private readonly cvUrlCache = new Map<string, string>();
  private restoreBodyClass: (() => void) | null = null;

  constructor(
    private applicationsStore: JobApplicationStoreService,
    private offersStore: JobOfferStoreService,
    private partnersStore: PartnershipStoreService
  ) {}

  ngOnInit(): void {
    this.restoreBodyClass = applyBackofficeBodyClass();
    this.sub.add(this.offersStore.items$.subscribe((items) => (this.offers = items)));
    this.sub.add(this.partnersStore.items$.subscribe((items) => (this.partners = items)));
    this.reloadOffers();
    this.reloadPartners();
    this.refreshApplications();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.restoreBodyClass?.();
    this.restoreBodyClass = null;
    for (const url of this.cvUrlCache.values()) {
      if (url.startsWith('blob:')) {
        URL.revokeObjectURL(url);
      }
    }
    this.cvUrlCache.clear();
  }

  get selectedOffer(): JobOffer | null {
    return this.offers.find((o) => o.id === this.selectedOfferId) ?? null;
  }

  get selectedOfferApplicationsCount(): number {
    if (!this.selectedOfferId) return 0;
    return this.applications.filter((a) => a.offerId === this.selectedOfferId).length;
  }

  get canAnalyze(): boolean {
    return !!this.selectedOfferId && !this.isAnalyzing && !this.isApplyingAutoStatus;
  }

  get referenceLoadError(): string | null {
    const messages = [this.offersLoadError, this.partnersLoadError].filter(
      (message): message is string => !!message
    );
    return messages.length ? Array.from(new Set(messages)).join(' ') : null;
  }

  statusLabel(status: JobApplicationStatus): string {
    switch (status) {
      case 'PENDING':
        return 'Pending';
      case 'REVIEWED':
        return 'Reviewed';
      case 'SHORTLISTED':
        return 'Shortlisted';
      case 'ACCEPTED':
        return 'Accepted';
      case 'REJECTED':
        return 'Rejected';
      default:
        return status;
    }
  }

  offerLabel(id: string): string {
    return this.offers.find((o) => o.id === id)?.title ?? id;
  }

  refreshApplications(): void {
    this.loading = true;
    this.loadingError = null;
    this.sub.add(
      this.applicationsStore.listAll().subscribe({
        next: (items) => {
          this.applications = items;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadingError = 'Impossible de charger les candidatures.';
        },
      })
    );
  }

  analyzeCandidates(): void {
    this.evaluationError = null;
    this.evaluationInfo = null;

    const offer = this.selectedOffer;
    if (!offer) {
      this.evaluationError = 'Selectionne une offre avant de lancer l analyse.';
      return;
    }

    this.isAnalyzing = true;
    const source = this.applications.filter((a) => a.offerId === offer.id);

    if (!source.length) {
      this.analyzedCandidates = [];
      this.topCandidates = [];
      this.scoreSummary = this.emptySummary();
      this.evaluationInfo = 'Aucune candidature trouvee pour cette offre.';
      this.isAnalyzing = false;
      return;
    }

    const offerKeywords = this.extractKeywords(this.buildOfferReferenceText(offer));
    const views = source
      .map((application) => this.computeCandidateScore(application, offerKeywords))
      .sort((a, b) => {
        if (b.compatibilityScore !== a.compatibilityScore) return b.compatibilityScore - a.compatibilityScore;
        return a.applicantName.localeCompare(b.applicantName);
      });

    const safeTop = this.normalizedTopCount();
    this.analyzedCandidates = views;
    this.topCandidates = views.slice(0, safeTop);
    this.scoreSummary = this.computeSummary(this.topCandidates, this.minCompatibility);
    this.isAnalyzing = false;

    if (this.autoStatusEnabled) {
      this.applyAutomaticStatuses();
    }
  }

  applyAutomaticStatuses(): void {
    if (!this.analyzedCandidates.length) {
      this.evaluationError = 'Aucune analyse disponible. Clique d abord sur "Analyser les CV".';
      return;
    }

    const updates = this.analyzedCandidates
      .map((candidate) => ({
        id: candidate.id,
        nextStatus: (candidate.compatibilityScore >= this.minCompatibility ? 'ACCEPTED' : 'REJECTED') as JobApplicationStatus,
      }))
      .filter((item) => {
        const current = this.applications.find((a) => a.id === item.id);
        return current && current.status !== item.nextStatus;
      });

    if (!updates.length) {
      this.evaluationInfo = 'Les statuts sont deja alignes avec la regle automatique.';
      return;
    }

    this.isApplyingAutoStatus = true;
    this.evaluationError = null;
    this.evaluationInfo = null;

    let remaining = updates.length;
    let failed = 0;

    for (const update of updates) {
      this.updatingStatusIds.add(update.id);
      this.sub.add(
        this.applicationsStore
          .updateReview(update.id, {
            status: update.nextStatus,
            reviewerNotes: `Auto-evaluation threshold ${this.minCompatibility}%`,
          })
          .subscribe({
            next: (saved) => {
              this.patchApplicationStatus(saved.id, saved.status);
              this.completeAutoStatusUpdate(update.id, --remaining, failed);
            },
            error: () => {
              failed += 1;
              this.completeAutoStatusUpdate(update.id, --remaining, failed);
            },
          })
      );
    }
  }

  updateCandidateStatus(candidate: CandidateMatchView, nextStatus: JobApplicationStatus): void {
    if (this.autoStatusEnabled) return;
    if (candidate.status === nextStatus) return;

    this.evaluationError = null;
    this.evaluationInfo = null;
    this.updatingStatusIds.add(candidate.id);

    this.sub.add(
      this.applicationsStore
        .updateReview(candidate.id, { status: nextStatus, reviewerNotes: candidate.reviewerNotes ?? null })
        .subscribe({
          next: (saved) => {
            this.patchApplicationStatus(saved.id, saved.status);
            this.updatingStatusIds.delete(candidate.id);
          },
          error: (err: unknown) => {
            this.updatingStatusIds.delete(candidate.id);
            this.evaluationError = this.buildStatusErrorMessage(err);
          },
        })
    );
  }

  isStatusUpdating(applicationId: string): boolean {
    return this.updatingStatusIds.has(applicationId);
  }

  resolveCvLink(cvDataUrl?: string | null): string | null {
    const raw = cvDataUrl?.trim();
    if (!raw) return null;
    if (!raw.startsWith('data:')) return raw;

    const cached = this.cvUrlCache.get(raw);
    if (cached) return cached;

    const match = raw.match(/^data:([^;,]+)?;base64,(.*)$/s);
    if (!match) return null;

    const mime = match[1] || 'application/octet-stream';
    const payload = match[2];

    try {
      const byteChars = atob(payload);
      const bytes = new Uint8Array(byteChars.length);
      for (let i = 0; i < byteChars.length; i++) {
        bytes[i] = byteChars.charCodeAt(i);
      }
      const blobUrl = URL.createObjectURL(new Blob([bytes], { type: mime }));
      this.cvUrlCache.set(raw, blobUrl);
      return blobUrl;
    } catch {
      return null;
    }
  }

  private completeAutoStatusUpdate(applicationId: string, remaining: number, failed: number): void {
    this.updatingStatusIds.delete(applicationId);
    if (remaining > 0) return;

    this.isApplyingAutoStatus = false;
    this.scoreSummary = this.computeSummary(this.topCandidates, this.minCompatibility);
    if (failed > 0) {
      this.evaluationError = `Mise a jour automatique terminee avec ${failed} erreur(s).`;
      return;
    }
    this.evaluationInfo = 'Statuts mis a jour automatiquement avec succes.';
  }

  private patchApplicationStatus(id: string, status: JobApplicationStatus): void {
    this.applications = this.applications.map((item) => (item.id === id ? { ...item, status } : item));
    this.analyzedCandidates = this.analyzedCandidates.map((item) => (item.id === id ? { ...item, status } : item));
    this.topCandidates = this.topCandidates.map((item) => (item.id === id ? { ...item, status } : item));
  }

  private buildOfferReferenceText(offer: JobOffer): string {
    return [
      offer.title,
      offer.contractType,
      offer.location ?? '',
      offer.salaryRange ?? '',
      offer.description ?? '',
      ...(offer.skills ?? []),
    ]
      .join(' ')
      .trim();
  }

  private computeCandidateScore(application: JobApplication, offerKeywords: string[]): CandidateMatchView {
    const offer = this.offers.find((o) => o.id === application.offerId);
    const partner = offer ? this.partners.find((p) => p.id === offer.partnershipId) : undefined;
    const cvText = this.extractTextFromCv(application.cvDataUrl);
    const profileText = `${cvText} ${application.message ?? ''} ${application.applicantName}`.toLowerCase();

    if (!offerKeywords.length) {
      return {
        ...application,
        offer,
        partner,
        compatibilityScore: 0,
        matchedKeywords: [],
        missingKeywords: [],
        keywordCount: 0,
        matchedCount: 0,
        cvReadable: !!cvText.trim(),
      };
    }

    const matchedKeywords = offerKeywords.filter((keyword) => profileText.includes(keyword));
    const missingKeywords = offerKeywords.filter((keyword) => !profileText.includes(keyword));
    const score = Math.min(100, Math.round((matchedKeywords.length / offerKeywords.length) * 100));

    return {
      ...application,
      offer,
      partner,
      compatibilityScore: score,
      matchedKeywords: matchedKeywords.slice(0, 20),
      missingKeywords: missingKeywords.slice(0, 20),
      keywordCount: offerKeywords.length,
      matchedCount: matchedKeywords.length,
      cvReadable: !!cvText.trim(),
    };
  }

  private computeSummary(candidates: CandidateMatchView[], threshold: number): ScoreSummary {
    if (!candidates.length) return this.emptySummary();

    const scores = candidates.map((c) => c.compatibilityScore);
    const average = Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length);
    const max = Math.max(...scores);
    const min = Math.min(...scores);
    const aboveThreshold = candidates.filter((c) => c.compatibilityScore >= threshold).length;
    const withoutCvText = candidates.filter((c) => !c.cvReadable).length;

    return { average, max, min, aboveThreshold, withoutCvText };
  }

  private emptySummary(): ScoreSummary {
    return { average: 0, max: 0, min: 0, aboveThreshold: 0, withoutCvText: 0 };
  }

  private extractTextFromCv(cvDataUrl?: string): string {
    const raw = cvDataUrl?.trim();
    if (!raw || !raw.startsWith('data:')) return '';

    const match = raw.match(/^data:([^;,]+)?;base64,(.*)$/s);
    if (!match) return '';

    const mime = (match[1] || '').toLowerCase();
    const payload = match[2];
    if (!payload) return '';
    if (mime.startsWith('application/pdf') || mime.startsWith('image/')) return '';

    const decoded = this.decodeBase64(payload);
    if (!decoded) return '';
    if (!mime.startsWith('text/') && !this.looksLikeReadableText(decoded)) return '';
    return decoded.slice(0, 300_000);
  }

  private decodeBase64(payload: string): string {
    try {
      const binary = atob(payload);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      return new TextDecoder().decode(bytes);
    } catch {
      return '';
    }
  }

  private looksLikeReadableText(content: string): boolean {
    if (!content.trim()) return false;
    const sample = content.slice(0, 1200);
    let readable = 0;
    for (let i = 0; i < sample.length; i++) {
      const code = sample.charCodeAt(i);
      if ((code >= 32 && code <= 126) || code === 10 || code === 13 || code === 9) {
        readable += 1;
      }
    }
    return readable / sample.length > 0.7;
  }

  private extractKeywords(text: string): string[] {
    return Array.from(
      new Set(
        text
          .toLowerCase()
          .replace(/[^a-z0-9\s-]/g, ' ')
          .split(/\s+/)
          .map((word) => word.trim())
          .filter((word) => word.length >= 4 && !STOP_WORDS.has(word))
      )
    );
  }

  private normalizedTopCount(): number {
    return Math.max(1, Math.min(50, Math.floor(this.topCount || 1)));
  }

  private buildStatusErrorMessage(err: unknown): string {
    const fallback = 'Mise a jour du statut impossible.';
    if (!(err instanceof HttpErrorResponse)) return fallback;
    if (err.status === 0) return `Backend inaccessible. Verifie que le service tourne sur ${environment.partnerApiBaseUrl}.`;
    if (err.status === 404) return 'Endpoint de mise a jour non trouve. Redemarre les services backend.';
    return fallback;
  }

  private reloadOffers(): void {
    this.offersLoadError = null;
    this.sub.add(
      this.offersStore.reload().subscribe({
        next: () => {
          this.offersLoadError = null;
        },
        error: (err: unknown) => {
          this.offersLoadError = this.buildReferenceErrorMessage(err, 'Impossible de charger les offres.');
        },
      })
    );
  }

  private reloadPartners(): void {
    this.partnersLoadError = null;
    this.sub.add(
      this.partnersStore.reload().subscribe({
        next: () => {
          this.partnersLoadError = null;
        },
        error: (err: unknown) => {
          this.partnersLoadError = this.buildReferenceErrorMessage(err, 'Impossible de charger les partenaires.');
        },
      })
    );
  }

  private buildReferenceErrorMessage(err: unknown, fallback: string): string {
    if (!(err instanceof HttpErrorResponse)) return fallback;
    if (err.status === 0) return `Backend inaccessible. Verifie que le service tourne sur ${environment.partnerApiBaseUrl}.`;
    if (err.status === 404) return 'Endpoint backend introuvable pour cette page.';
    return fallback;
  }
}
