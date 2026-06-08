import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { JobContractType, JobOffer } from '../../../shared/models/job-offer.models';
import { Partnership } from '../../../shared/models/partnership.models';
import { JobOfferStoreService } from '../../../shared/stores/job-offer-store.service';
import { PartnershipStoreService } from '../../../shared/stores/partnership-store.service';

type JobOfferView = JobOffer & { partner?: Partnership };

@Component({
  selector: 'app-jobs',
  standalone: false,
  templateUrl: './jobs.component.html',
  styleUrls: ['./jobs.component.css'],
  host: { style: 'display:block' },
})
export class JobsComponent implements OnInit, OnDestroy {
  query = '';
  contractType: JobContractType | 'ALL' = 'ALL';
  page = 1;
  pageSize = 9;
  offersLoadError: string | null = null;
  partnersLoadError: string | null = null;

  offers: JobOffer[] = [];
  partners: Partnership[] = [];
  private readonly sub = new Subscription();

  readonly contractTypes: Array<JobContractType | 'ALL'> = ['ALL', 'CDI', 'CDD', 'Stage', 'Alternance', 'Freelance', 'Autre'];

  constructor(private offersStore: JobOfferStoreService, private partnersStore: PartnershipStoreService) {}

  ngOnInit(): void {
    this.sub.add(this.offersStore.items$.subscribe((o) => (this.offers = o)));
    this.sub.add(this.partnersStore.items$.subscribe((p) => (this.partners = p)));
    this.reloadOffers();
    this.reloadPartners();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  private partnerById(id: string): Partnership | undefined {
    return this.partners.find((p) => p.id === id);
  }

  get loadError(): string | null {
    return this.offersLoadError ?? this.partnersLoadError;
  }

  get activeViews(): JobOfferView[] {
    const partnerActive = new Map(this.partners.map((p) => [p.id, p.isActive]));
    return this.offers
      .filter((o) => o.isActive && (partnerActive.get(o.partnershipId) ?? true))
      .map((o) => ({ ...o, partner: this.partnerById(o.partnershipId) }))
      .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  }

  get filtered(): JobOfferView[] {
    const q = this.query.trim().toLowerCase();
    const base = this.activeViews.filter((o) => (this.contractType === 'ALL' ? true : o.contractType === this.contractType));
    const searched = q
      ? base.filter((o) => {
          const hay = `${o.title} ${o.location ?? ''} ${o.contractType} ${o.partner?.name ?? ''} ${(o.skills ?? []).join(' ')}`.toLowerCase();
          return hay.includes(q);
        })
      : base;
    return searched;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }

  get paged(): JobOfferView[] {
    const safePage = Math.min(Math.max(1, this.page), this.totalPages);
    const start = (safePage - 1) * this.pageSize;
    return this.filtered.slice(start, start + this.pageSize);
  }

  onQueryChange(v: string): void {
    this.query = v;
    this.page = 1;
  }

  onContractChange(v: JobContractType | 'ALL'): void {
    this.contractType = v;
    this.page = 1;
  }

  cardImage(o: JobOfferView): string {
    return (
      o.imageDataUrl ||
      o.partner?.logoDataUrl ||
      'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/fi_6582502.png'
    );
  }

  private reloadOffers(): void {
    this.sub.add(
      this.offersStore.reload().subscribe({
        next: () => {
          this.offersLoadError = null;
          this.page = Math.min(this.page, this.totalPages);
        },
        error: (error: unknown) => {
          this.offersLoadError = this.buildBackendErrorMessage(error, 'Unable to load job offers right now.');
        },
      })
    );
  }

  private reloadPartners(): void {
    this.sub.add(
      this.partnersStore.reload().subscribe({
        next: () => {
          this.partnersLoadError = null;
        },
        error: (error: unknown) => {
          this.partnersLoadError = this.buildBackendErrorMessage(error, 'Unable to load partner information right now.');
        },
      })
    );
  }

  private buildBackendErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    const apiMessage =
      typeof error.error === 'object' && error.error && typeof (error.error as { message?: unknown }).message === 'string'
        ? (error.error as { message: string }).message.trim()
        : '';

    if (apiMessage) {
      return apiMessage;
    }

    if (error.status === 0) {
      return `Job data is unavailable right now. Check that the partner backend is running on ${environment.partnerApiBaseUrl}.`;
    }

    return fallback;
  }
}


