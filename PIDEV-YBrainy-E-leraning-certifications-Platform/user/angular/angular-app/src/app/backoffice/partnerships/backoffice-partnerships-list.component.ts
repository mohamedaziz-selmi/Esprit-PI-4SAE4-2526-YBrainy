import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { Partnership } from '../../shared/models/partnership.models';
import { PartnershipStoreService } from '../../shared/stores/partnership-store.service';
import { BackofficeDashboardSidebarComponent } from '../shared/backoffice-dashboard-sidebar.component';
import { applyBackofficeBodyClass } from '../utils/backoffice-body-class';

@Component({
  selector: 'app-backoffice-partnerships-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackofficeDashboardSidebarComponent],
  templateUrl: './backoffice-partnerships-list.component.html',
  styleUrl: './backoffice-partnerships-list.component.css',
  host: { style: 'display:block' },
})
export class BackofficePartnershipsListComponent implements OnInit, OnDestroy {
  query = '';
  page = 1;
  pageSize = 8;
  loadError: string | null = null;

  items: Partnership[] = [];
  private readonly sub = new Subscription();
  private restoreBodyClass: (() => void) | null = null;

  constructor(private store: PartnershipStoreService) {}

  ngOnInit(): void {
    this.restoreBodyClass = applyBackofficeBodyClass();
    this.sub.add(this.store.items$.subscribe((items) => (this.items = items)));
    this.reloadPartnerships();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.restoreBodyClass?.();
    this.restoreBodyClass = null;
  }

  get filtered(): Partnership[] {
    const q = this.query.trim().toLowerCase();
    const base = q
      ? this.items.filter((p) => {
          const hay = `${p.name} ${p.email ?? ''} ${p.phone ?? ''} ${p.industry ?? ''} ${p.website ?? ''}`.toLowerCase();
          return hay.includes(q);
        })
      : this.items;
    return base.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }

  get safePage(): number {
    return Math.min(Math.max(1, this.page), this.totalPages);
  }

  get paged(): Partnership[] {
    const start = (this.safePage - 1) * this.pageSize;
    return this.filtered.slice(start, start + this.pageSize);
  }

  get pageStart(): number {
    if (this.filtered.length === 0) return 0;
    return (this.safePage - 1) * this.pageSize + 1;
  }

  get pageEnd(): number {
    if (this.filtered.length === 0) return 0;
    return Math.min(this.safePage * this.pageSize, this.filtered.length);
  }

  get pageNumbers(): number[] {
    const windowSize = 5;
    const total = this.totalPages;
    const current = this.safePage;
    const half = Math.floor(windowSize / 2);
    let start = Math.max(1, current - half);
    let end = Math.min(total, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);

    const pages: number[] = [];
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  onQueryChange(v: string): void {
    this.query = v;
    this.page = 1;
  }

  goToPreviousPage(): void {
    if (this.safePage > 1) this.page = this.safePage - 1;
  }

  goToNextPage(): void {
    if (this.safePage < this.totalPages) this.page = this.safePage + 1;
  }

  delete(id: string): void {
    const p = this.store.getById(id);
    const ok = window.confirm(`Supprimer le partenaire "${p?.name ?? ''}" ?`);
    if (!ok) return;
    this.sub.add(
      this.store.delete(id).subscribe({
        next: () => {
          this.loadError = null;
        },
        error: (err: unknown) => {
          this.loadError = this.buildBackendErrorMessage(err, 'Suppression du partenaire impossible pour le moment.');
        },
      })
    );
  }

  resetDemo(): void {
    this.reloadPartnerships();
  }

  private reloadPartnerships(): void {
    this.loadError = null;
    this.sub.add(
      this.store.reload().subscribe({
        next: () => {
          this.loadError = null;
        },
        error: (err: unknown) => {
          this.loadError = this.buildBackendErrorMessage(err, 'Impossible de charger les partenaires pour le moment.');
        },
      })
    );
  }

  private buildBackendErrorMessage(err: unknown, fallback: string): string {
    if (!(err instanceof HttpErrorResponse)) return fallback;
    if (err.status === 0) return `Backend inaccessible. Verifie que le service tourne sur ${environment.partnerApiBaseUrl}.`;
    if (err.status === 404) return 'Endpoint partenaires introuvable sur le backend.';
    return fallback;
  }
}
