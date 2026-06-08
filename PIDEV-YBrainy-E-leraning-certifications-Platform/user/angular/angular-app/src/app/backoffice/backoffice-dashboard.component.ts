import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { getDisplayName, logout } from '../auth/keycloak.service';

@Component({
  selector: 'app-backoffice-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './backoffice-dashboard.component.html',
  styleUrl: './backoffice-dashboard.component.css',
  host: { style: 'display:block' },
})
export class BackofficeDashboardComponent implements OnInit, OnDestroy {
  dashboardUrl: string = 'assets/backoffice/index.html';
  safeDashboardUrl: SafeResourceUrl;
  adminDisplayName = 'Admin';
  userRole: string = 'ADMIN';
  private readonly sub = new Subscription();
  private readonly embeddedNavPaths = new Set([
    '/dashboard/partners',
    '/dashboard/partners/new',
    '/dashboard/job-offers',
    '/dashboard/job-offers/new',
    '/dashboard/cv-submissions',
    '/dashboard/applications',
    '/dashboard/applications/evaluate',
    '/dashboard/ai-application',
    '/dashboard/events',
    '/dashboard/events/new',
    '/dashboard/forum/threads',
    '/dashboard/forum/categories',
  ]);

  private readonly allowedPages = new Set([
    'index.html',
    'courses.html',
    'lessons.html',
    'app-calender.html',
    'celandar.html',
    'cv-list.html',
  ]);

  constructor(
    private sanitizer: DomSanitizer,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.safeDashboardUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.dashboardUrl);
  }

  ngOnInit(): void {
    window.addEventListener('message', this.handleEmbeddedNavigation);
    this.adminDisplayName = getDisplayName() ?? 'Admin';
    const raw = localStorage.getItem('bb_user_session_v1');
    if (raw) {
      try { this.userRole = JSON.parse(raw)?.role ?? 'ADMIN'; } catch {}
    }
    this.sub.add(
      combineLatest([this.route.data, this.route.queryParamMap]).subscribe(([data, qpm]) => {
        const page = (data?.['page'] as string | undefined) ?? 'index.html';
        const safePage = this.allowedPages.has(page) ? page : 'index.html';
        this.dashboardUrl = `assets/backoffice/${safePage}`;
        this.safeDashboardUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.dashboardUrl);
      })
    );
  }

  ngOnDestroy(): void {
    window.removeEventListener('message', this.handleEmbeddedNavigation);
    this.sub.unsubscribe();
  }

  async onLogout(): Promise<void> {
    await logout();
  }

  private readonly handleEmbeddedNavigation = (event: MessageEvent): void => {
    if (event.origin !== window.location.origin) {
      return;
    }

    const type = typeof event.data?.type === 'string' ? event.data.type : '';
    const path = typeof event.data?.path === 'string' ? event.data.path.trim() : '';

    if (type !== 'yb:navigate' || !this.embeddedNavPaths.has(path)) {
      return;
    }

    void this.router.navigateByUrl(path);
  };
}
