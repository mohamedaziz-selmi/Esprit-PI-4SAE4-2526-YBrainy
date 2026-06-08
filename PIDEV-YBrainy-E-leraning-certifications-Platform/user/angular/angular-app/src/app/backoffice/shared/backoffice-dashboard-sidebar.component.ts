import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { Subscription, filter } from 'rxjs';

type SidebarSection =
  | 'dashboard'
  | 'student'
  | 'teacher'
  | 'courses'
  | 'assessment'
  | 'forum'
  | 'reports'
  | 'recruitment'
  | 'fileManager'
  | 'apps';

@Component({
  selector: 'app-backoffice-dashboard-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './backoffice-dashboard-sidebar.component.html',
})
export class BackofficeDashboardSidebarComponent implements OnInit, OnDestroy {
  currentUrl = '';

  private readonly sub = new Subscription();
  private readonly expandedOverrides: Partial<Record<SidebarSection, boolean>> = {};

  constructor(private readonly router: Router) {
    this.currentUrl = this.normalizeUrl(this.router.url);
  }

  ngOnInit(): void {
    this.sub.add(
      this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
        this.currentUrl = this.normalizeUrl(event.urlAfterRedirects);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  toggle(section: SidebarSection): void {
    this.expandedOverrides[section] = !this.isExpanded(section);
  }

  isExpanded(section: SidebarSection): boolean {
    const override = this.expandedOverrides[section];
    return override ?? this.isSectionActive(section);
  }

  isSectionActive(section: SidebarSection): boolean {
    switch (section) {
      case 'dashboard':
        return this.matchesExact('/dashboard') || this.matchesExact('/dashboard/finance');
      case 'student':
        return false;
      case 'teacher':
        return false;
      case 'courses':
        return this.matchesPrefix('/dashboard/courses') || this.matchesPrefix('/dashboard/lessons') || this.matchesExact('/dashboard/calendar');
      case 'assessment':
        return false;
      case 'forum':
        return this.matchesPrefix('/dashboard/forum/categories') || this.matchesPrefix('/dashboard/forum/threads');
      case 'reports':
        return false;
      case 'recruitment':
        return (
          this.matchesPrefix('/dashboard/partners') ||
          this.matchesPrefix('/dashboard/job-offers') ||
          this.matchesPrefix('/dashboard/applications') ||
          this.matchesExact('/dashboard/ai-application')
        );
      case 'fileManager':
        return false;
      case 'apps':
        return false;
      default:
        return false;
    }
  }

  isActiveExact(route: string): boolean {
    return this.matchesExact(route);
  }

  isActivePrefix(route: string): boolean {
    return this.matchesPrefix(route);
  }

  isShopActive(): boolean {
    return (
      this.matchesExact('/dashboard/packs') ||
      this.matchesExact('/dashboard/packsorder') ||
      this.matchesExact('/dashboard/categories')
    );
  }

  private matchesExact(route: string): boolean {
    return this.currentUrl === route;
  }

  private matchesPrefix(route: string): boolean {
    return this.currentUrl === route || this.currentUrl.startsWith(`${route}/`);
  }

  private normalizeUrl(url: string): string {
    return (url || '').split('?')[0].split('#')[0];
  }
}
