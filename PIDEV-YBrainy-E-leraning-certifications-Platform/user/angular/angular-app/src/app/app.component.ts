import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { InteractionTrackingService } from './tracking/interaction-tracking.service';
import { Router } from '@angular/router';
import { getDisplayName, isAuthenticated } from './auth/keycloak.service';
import { FocusModeService } from './frontoffice/services/focus-mode.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'angular-app';
  private readonly spaRoutePrefixes = [
    '/about',
    '/calendar',
    '/courses',
    '/dashboard',
    '/forum',
    '/forgot-password',
    '/learning-paths',
    '/login',
    '/messages',
    '/my-dashboard',
    '/my-learning',
    '/packs',
    '/payment',
    '/profile',
    '/resources',
    '/services',
    '/signup',
    '/wishlist',
  ];

  constructor(
    tracking: InteractionTrackingService,
    private router: Router,
    public focusMode: FocusModeService
  ) {
    tracking.init();

    if (isAuthenticated() && !sessionStorage.getItem('welcomeShown')) {
      sessionStorage.setItem('welcomeShown', '1');
      const name = getDisplayName() ?? 'Student';
      setTimeout(() => {
        window.alert(`Welcome ${name}`);
      }, 0);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
      return;
    }

    const target = event.target as HTMLElement | null;
    const anchor = target?.closest('a[href]') as HTMLAnchorElement | null;
    if (!anchor) {
      return;
    }

    const targetAttr = (anchor.getAttribute('target') || '').trim().toLowerCase();
    if (targetAttr && targetAttr !== '_self') {
      return;
    }

    const rawHref = (anchor.getAttribute('href') || '').trim();
    if (!rawHref || rawHref.startsWith('#') || rawHref.toLowerCase().startsWith('javascript:')) {
      return;
    }

    let url: URL;
    try {
      url = new URL(anchor.href, window.location.origin);
    } catch {
      return;
    }

    if (url.origin !== window.location.origin || url.pathname.startsWith('/assets/')) {
      return;
    }

    const path = `${url.pathname}${url.search}${url.hash}`;
    const isSpaRoute =
      path === '/' ||
      this.spaRoutePrefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`));

    if (!isSpaRoute) {
      return;
    }

    event.preventDefault();
    void this.router.navigateByUrl(path);
  }
}
