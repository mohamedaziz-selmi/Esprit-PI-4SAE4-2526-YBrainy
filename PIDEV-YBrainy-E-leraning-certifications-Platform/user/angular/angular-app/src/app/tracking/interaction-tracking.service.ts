import { Injectable, NgZone, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { getKeycloak, getRealmRoles, isAuthenticated } from '../auth/keycloak.service';
import { environment } from '../../environments/environment';

type InteractionEventType = 'CLICK' | 'ROUTE' | 'ENGAGEMENT';

interface InteractionEventDto {
  eventType: InteractionEventType;
  occurredAtEpochMs: number;
  route?: string;
  durationMs?: number;
  metadata?: Record<string, unknown>;
}

@Injectable({ providedIn: 'root' })
export class InteractionTrackingService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  private readonly baseUrl = `${environment.apiBaseUrl}/api/tracking`;

  private initialized = false;
  private queue: InteractionEventDto[] = [];

  private flushTimer: number | null = null;
  private engagementTimer: number | null = null;

  private currentRoute = '';
  private engaged = false;
  private engagedStartedAt = 0;

  init(): void {
    if (this.initialized) return;
    this.initialized = true;

    this.zone.runOutsideAngular(() => {
      this.currentRoute = this.router.url;

      this.router.events
        .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
        .subscribe((e) => {
          this.currentRoute = e.urlAfterRedirects;
          this.enqueue('ROUTE', { to: e.urlAfterRedirects });
        });

      document.addEventListener('click', this.onDocumentClick, true);
      window.addEventListener('focus', this.onFocus, true);
      window.addEventListener('blur', this.onBlur, true);
      document.addEventListener('visibilitychange', this.onVisibilityChange, true);
      window.addEventListener('beforeunload', this.onBeforeUnload, true);

      this.setEngaged(true);
      this.startFlushLoop();
      this.startEngagementHeartbeat();
    });
  }

  private shouldTrack(): boolean {
    if (!isAuthenticated()) return false;
    return !getRealmRoles().map((r) => r.toUpperCase()).includes('ADMIN');
  }

  private enqueue(eventType: InteractionEventType, metadata?: Record<string, unknown>, durationMs?: number): void {
    if (!this.shouldTrack()) return;

    this.queue.push({
      eventType,
      occurredAtEpochMs: Date.now(),
      route: this.currentRoute,
      durationMs,
      metadata,
    });

    if (this.queue.length >= 25) {
      void this.flush();
    }
  }

  private flush = async (): Promise<void> => {
    if (!this.shouldTrack()) {
      this.queue = [];
      return;
    }

    if (this.queue.length === 0) return;

    const subject = String(getKeycloak().subject ?? '');
    if (!subject) return;

    const payload = {
      keycloakSubject: subject,
      role: (getRealmRoles()[0] ?? ''),
      events: this.queue.splice(0, this.queue.length),
    };

    try {
      await this.http.post<void>(`${this.baseUrl}/events`, payload).toPromise();
    } catch {
      this.queue.unshift(...payload.events);
      if (this.queue.length > 200) {
        this.queue = this.queue.slice(-200);
      }
    }
  };

  private startFlushLoop(): void {
    if (this.flushTimer) return;
    this.flushTimer = window.setInterval(() => {
      void this.flush();
    }, 5000);
  }

  private startEngagementHeartbeat(): void {
    if (this.engagementTimer) return;
    this.engagementTimer = window.setInterval(() => {
      if (!this.engaged) return;
      const now = Date.now();
      const delta = now - this.engagedStartedAt;
      if (delta >= 10000) {
        this.engagedStartedAt = now;
        this.enqueue('ENGAGEMENT', { kind: 'active' }, delta);
      }
    }, 10000);
  }

  private setEngaged(next: boolean): void {
    const now = Date.now();

    if (this.engaged && !next) {
      const delta = now - this.engagedStartedAt;
      if (delta > 0) {
        this.enqueue('ENGAGEMENT', { kind: 'active' }, delta);
      }
    }

    if (!this.engaged && next) {
      this.engagedStartedAt = now;
    }

    this.engaged = next;
  }

  private onFocus = (): void => {
    this.setEngaged(true);
  };

  private onBlur = (): void => {
    this.setEngaged(false);
  };

  private onVisibilityChange = (): void => {
    this.setEngaged(document.visibilityState === 'visible');
  };

  private onBeforeUnload = (): void => {
    this.setEngaged(false);
  };

  private onDocumentClick = (ev: MouseEvent): void => {
    const target = ev.target as Element | null;
    if (!target) return;

    const el = (target.closest('a,button,[role="button"],input,select,textarea,label') || target) as Element;

    const tag = el.tagName?.toLowerCase();
    const id = (el as HTMLElement).id || undefined;
    const className = (el as HTMLElement).className || undefined;
    const text = (el as HTMLElement).innerText ? (el as HTMLElement).innerText.trim().slice(0, 80) : undefined;

    this.enqueue('CLICK', {
      tag,
      id,
      className,
      text,
      x: ev.clientX,
      y: ev.clientY,
    });
  };
}
