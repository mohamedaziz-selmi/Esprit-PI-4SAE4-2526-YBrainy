import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { JobOffer } from '../../shared/models/job-offer.models';
import { Partnership } from '../../shared/models/partnership.models';
import { JobOfferStoreService } from '../../shared/stores/job-offer-store.service';
import { PartnershipStoreService } from '../../shared/stores/partnership-store.service';
import { BackofficeDashboardSidebarComponent } from '../shared/backoffice-dashboard-sidebar.component';
import { applyBackofficeBodyClass } from '../utils/backoffice-body-class';

@Component({
  selector: 'app-backoffice-job-offers-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackofficeDashboardSidebarComponent],
  templateUrl: './backoffice-job-offers-list.component.html',
  styleUrl: './backoffice-job-offers-list.component.css',
  host: { style: 'display:block' },
})
export class BackofficeJobOffersListComponent implements OnInit, OnDestroy {
  query = '';
  partnerId: string = '';
  page = 1;
  pageSize = 8;
  offersLoadError: string | null = null;
  partnersLoadError: string | null = null;

  offers: JobOffer[] = [];
  partners: Partnership[] = [];

  private readonly sub = new Subscription();
  private restoreBodyClass: (() => void) | null = null;

  constructor(private offersStore: JobOfferStoreService, private partnersStore: PartnershipStoreService) {}

  ngOnInit(): void {
    this.restoreBodyClass = applyBackofficeBodyClass();
    this.sub.add(this.offersStore.items$.subscribe((o) => (this.offers = o)));
    this.sub.add(this.partnersStore.items$.subscribe((p) => (this.partners = p)));
    this.reloadOffers();
    this.reloadPartners();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.restoreBodyClass?.();
    this.restoreBodyClass = null;
  }

  partnerName(id: string): string {
    return this.partners.find((p) => p.id === id)?.name ?? '-';
  }

  get filtered(): JobOffer[] {
    const q = this.query.trim().toLowerCase();
    const base = this.offers.filter((o) => (this.partnerId ? o.partnershipId === this.partnerId : true));
    const searched = q
      ? base.filter((o) => {
          const hay = `${o.title} ${o.location ?? ''} ${o.contractType} ${this.partnerName(o.partnershipId)}`.toLowerCase();
          return hay.includes(q);
        })
      : base;
    return searched.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }

  get paged(): JobOffer[] {
    const safePage = Math.min(Math.max(1, this.page), this.totalPages);
    const start = (safePage - 1) * this.pageSize;
    return this.filtered.slice(start, start + this.pageSize);
  }

  get loadError(): string | null {
    const messages = [this.offersLoadError, this.partnersLoadError].filter(
      (message): message is string => !!message
    );
    return messages.length ? Array.from(new Set(messages)).join(' ') : null;
  }

  delete(id: string): void {
    const o = this.offersStore.getById(id);
    const ok = window.confirm(`Supprimer l'offre "${o?.title ?? ''}" ?`);
    if (!ok) return;
    this.sub.add(
      this.offersStore.delete(id).subscribe({
        next: () => {
          this.offersLoadError = null;
        },
        error: (err: unknown) => {
          this.offersLoadError = this.buildBackendErrorMessage(err, 'Suppression de l offre impossible pour le moment.');
        },
      })
    );
  }

  resetDemo(): void {
    this.reloadOffers();
  }

  private reloadOffers(): void {
    this.offersLoadError = null;
    this.sub.add(
      this.offersStore.reload().subscribe({
        next: () => {
          this.offersLoadError = null;
        },
        error: (err: unknown) => {
          this.offersLoadError = this.buildBackendErrorMessage(err, 'Impossible de charger les offres pour le moment.');
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
          this.partnersLoadError = this.buildBackendErrorMessage(err, 'Impossible de charger les partenaires pour le moment.');
        },
      })
    );
  }

  private buildBackendErrorMessage(err: unknown, fallback: string): string {
    if (!(err instanceof HttpErrorResponse)) return fallback;
    if (err.status === 0) return `Backend inaccessible. Verifie que le service tourne sur ${environment.partnerApiBaseUrl}.`;
    if (err.status === 404) return 'Endpoint backend introuvable pour cette page.';
    return fallback;
  }
}




