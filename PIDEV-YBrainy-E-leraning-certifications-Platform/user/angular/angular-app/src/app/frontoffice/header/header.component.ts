import { Component, AfterViewInit, OnDestroy, ElementRef, HostListener, ChangeDetectorRef } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import {
  getDisplayName,
  getRealmRoles,
  isAuthenticated,
  redirectToAppLogin,
} from '../../auth/keycloak.service';
import { CartHistory } from '../models/cart.model';
import { CartService } from '../services/cart.service';
import { FocusModeService, FocusModeState } from '../services/focus-mode.service';
import { GazePreferenceService } from '../services/gaze-preference.service';
import { UserService } from '../services/user.service';
import { UserSessionService } from '../../tracking/user-session.service';
import { NotificationApiService } from '../services/notification-api.service';
import { AuthService } from '../services/auth.service';
import { NotificationResponse } from '../models/forum.models';

declare var $: any;

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
  host: { 'style': 'display:block' }
})
export class HeaderComponent implements AfterViewInit, OnDestroy {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
  isHome = false;
  menuOpen = false;
  profileMenuOpen = false;
  showModeDropdown = false;
  cartDropdownOpen = false;
  showHistory = false;
  headerProfileImage: string | null = null;
  headerProfileName: string | null = null;
  cartHistory: CartHistory[] = [];
  checkoutInProgress = false;
  showCheckoutToast = false;
  checkoutToastType: 'success' | 'error' = 'success';
  checkoutToastTitle = '';
  checkoutToastMessage = '';
  gazeTrackingEnabled = true;
  focusModeState: FocusModeState | null = null;

  // Notification state
  notifOpen = false;
  notifications: NotificationResponse[] = [];
  unreadCount = 0;
  notifLoading = false;
  private notifTimer: ReturnType<typeof setInterval> | null = null;

  private mobileNav: any;
  private readonly subscriptions = new Subscription();
  private checkoutToastTimer: ReturnType<typeof setTimeout> | null = null;
  private processingStripeSessionId: string | null = null;

  constructor(
    private el: ElementRef,
    private router: Router,
    public cartService: CartService,
    public focusMode: FocusModeService,
    private gazePreference: GazePreferenceService,
    private frontofficeUserService: UserService,
    private userSession: UserSessionService,
    private cdr: ChangeDetectorRef,
    private notifService: NotificationApiService,
    private authService: AuthService
  ) {
    this.syncIsHomeFromUrl(this.getCurrentUrl());
    this.gazeTrackingEnabled = this.gazePreference.current;
  }

  get authenticated(): boolean {
    return isAuthenticated();
  }

  get displayName(): string | null {
    return getDisplayName();
  }

  get resolvedDisplayName(): string | null {
    return this.headerProfileName || this.displayName;
  }

  get isAdminUser(): boolean {
    return getRealmRoles().some((role) => String(role).toUpperCase() === 'ADMIN');
  }

  get currentMode(): string {
    return this.userSession.getMode();
  }

  get currentRole(): string {
    return this.userSession.get()?.role || 'STUDENT';
  }

  get availableModes(): Array<'STUDENT' | 'INSTRUCTOR' | 'ADMIN'> {
    return this.userSession.getAvailableModes();
  }

  get cartItemCount(): number {
    return this.cartService.getCartItemCount();
  }

  getModeIcon(mode: string): string {
    if (mode === 'STUDENT') return '📚';
    if (mode === 'INSTRUCTOR') return '🎓';
    if (mode === 'ADMIN') return '👑';
    return '👤';
  }

  getModeLabel(mode: string): string {
    if (mode === 'STUDENT') return 'Student Mode';
    if (mode === 'INSTRUCTOR') return 'Instructor Mode';
    if (mode === 'ADMIN') return 'Admin Mode';
    return 'User';
  }

  switchMode(mode: 'STUDENT' | 'INSTRUCTOR' | 'ADMIN'): void {
    if (!this.userSession.canAccessMode(mode)) return;

    this.userSession.setMode(mode);
    this.showModeDropdown = false;

    const current = this.userSession.get();
    if (current) {
      const updated = { ...current, role: mode.toUpperCase() };
      localStorage.setItem('bb_user_session_v1', JSON.stringify(updated));
    }

    // Route based on mode
    if (mode === 'STUDENT') {
      this.router.navigate(['/courses']);
    } else if (mode === 'INSTRUCTOR') {
      this.router.navigate(['/dashboard/courses']);
    } else if (mode === 'ADMIN') {
      this.router.navigate(['/dashboard']);
    }

    // Force Angular to update the view
    this.cdr.detectChanges();
  }

  toggleModeDropdown(): void {
    this.showModeDropdown = !this.showModeDropdown;
  }

  toggleGlobalGazeTracking(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.gazePreference.setEnabled(input?.checked === true);
  }

  toggleFocusMode(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.focusMode.toggle(input?.checked === true);
  }

  toggleCartDropdown(event?: MouseEvent): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.cartDropdownOpen = !this.cartDropdownOpen;
    if (this.cartDropdownOpen) {
      this.showHistory = false;
      this.cartService.refreshCart();
      this.fetchHistory();
    }
  }

  toggleHistory(show: boolean): void {
    this.showHistory = show;
    if (show) {
      this.fetchHistory();
    }
  }

  onLogin(): void {
    redirectToAppLogin(window.location.href);
  }

  async onLogout(): Promise<void> {
    this.authService.logout();
  }

  removeFromCart(itemId: number, event: Event): void {
    event.stopPropagation();
    this.cartService.removeFromCart(itemId).subscribe({
      next: () => this.fetchHistory(),
      error: (err) => console.error('Error removing from cart', err)
    });
  }

  checkout(): void {
    if (this.checkoutInProgress) {
      return;
    }
    this.checkoutInProgress = true;

    this.cartService.createStripeCheckoutSession().subscribe({
      next: (session) => {
        if (!session?.checkoutUrl) {
          this.checkoutInProgress = false;
          this.showCheckoutMessage(
            'error',
            'Checkout failed',
            'Stripe checkout URL is missing. Please try again.'
          );
          return;
        }

        window.location.href = session.checkoutUrl;
      },
      error: (err) => {
        this.checkoutInProgress = false;
        console.error('Checkout failed', err);
        this.showCheckoutMessage(
          'error',
          'Checkout failed',
          err?.error?.message || err?.message || 'We could not complete checkout. Please try again.'
        );
      }
    });
  }

  closeCheckoutToast(): void {
    this.showCheckoutToast = false;
    if (this.checkoutToastTimer) {
      clearTimeout(this.checkoutToastTimer);
      this.checkoutToastTimer = null;
    }
  }

  getAvatarInitials(): string {
    const source = (this.resolvedDisplayName || '').trim();
    if (!source) return '?';
    const parts = source.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
    }
    return (parts[0]?.slice(0, 2) || '?').toUpperCase();
  }

  toggleProfileMenu(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.profileMenuOpen = !this.profileMenuOpen;
  }

  fetchHistory(): void {
    this.cartService.getHistory().subscribe({
      next: (history) => {
        this.cartHistory = [...(history ?? [])].reverse();
      },
      error: (err) => console.error('Error fetching history', err)
    });
  }

  // ── Notification methods ───────────────────────────────────────────────────

  toggleNotif(event?: MouseEvent): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.notifOpen = !this.notifOpen;
    if (this.notifOpen) {
      this.loadNotifications();
    }
  }

  loadNotifications(): void {
    const uid = this.authService.currentUserId;
    if (!uid) return;
    this.notifLoading = true;
    this.notifService.getAll(uid).subscribe({
      next: (list) => {
        this.notifications = list.sort((a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        this.unreadCount = list.filter(n => !n.read).length;
        this.notifLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.notifLoading = false; }
    });
  }

  private refreshUnreadCount(): void {
    const uid = this.authService.currentUserId;
    if (!uid) return;
    this.notifService.getUnreadCount(uid).subscribe({
      next: (count) => { this.unreadCount = count; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  markNotifRead(n: NotificationResponse): void {
    if (!n.read) {
      this.notifService.markAsRead(n.id).subscribe({
        next: () => {
          n.read = true;
          this.unreadCount = Math.max(0, this.unreadCount - 1);
          this.cdr.detectChanges();
        },
        error: () => {}
      });
    }
    if (n.threadId) {
      this.router.navigate(['/forum', n.threadId]);
    }
    this.notifOpen = false;
  }

  markAllRead(): void {
    const uid = this.authService.currentUserId;
    if (!uid) return;
    this.notifService.markAllAsRead(uid).subscribe({
      next: () => {
        this.notifications.forEach(n => n.read = true);
        this.unreadCount = 0;
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  notifIcon(type: string): string {
    const map: Record<string, string> = {
      POST_CREATED: 'bi-chat-dots',
      BEST_ANSWER: 'bi-award',
      UPVOTE_RECEIVED: 'bi-hand-thumbs-up',
      COMMENT_CREATED: 'bi-chat',
      THREAD_CREATED: 'bi-pencil-square',
    };
    return map[type] ?? 'bi-bell';
  }

  notifIconColor(type: string): string {
    const map: Record<string, string> = {
      POST_CREATED: '#3b82f6',
      BEST_ANSWER: '#22c55e',
      UPVOTE_RECEIVED: '#f59e0b',
      COMMENT_CREATED: '#a78bfa',
      THREAD_CREATED: '#6366f1',
    };
    return map[type] ?? '#94a3b8';
  }

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  ngAfterViewInit(): void {
    const currentUrl = this.getCurrentUrl();
    this.handleStripeCheckoutReturn(currentUrl);
    this.loadHeaderUserData();
    if (this.authenticated) {
      this.cartService.refreshCart();
      this.refreshUnreadCount();
      this.notifTimer = setInterval(() => this.refreshUnreadCount(), 60000);
    }
    this.subscriptions.add(
      this.router.events
        .pipe(
          filter((e): e is NavigationEnd => e instanceof NavigationEnd)
        )
        .subscribe((e) => {
          this.syncIsHomeFromUrl(e.urlAfterRedirects);
          this.handleStripeCheckoutReturn(e.urlAfterRedirects);
          this.profileMenuOpen = false;
          this.loadHeaderUserData();
        })
    );

    this.subscriptions.add(
      this.cartService.cart$.subscribe(() => {
        if (this.cartDropdownOpen) {
          this.fetchHistory();
        }
      })
    );

    this.subscriptions.add(
      this.gazePreference.enabled$.subscribe((enabled) => {
        this.gazeTrackingEnabled = enabled;
      })
    );

    this.subscriptions.add(
      this.focusMode.state$.subscribe((state) => {
        this.focusModeState = state;
      })
    );

    this.initStickyHeader();
    this.initMegaMenu();
    this.initMobileNav();
  }

  ngOnDestroy(): void {
    if (this.canUseJquery()) {
      $(window).off('scroll.headerSticky');
    }
    if (this.checkoutToastTimer) {
      clearTimeout(this.checkoutToastTimer);
      this.checkoutToastTimer = null;
    }
    if (this.notifTimer) {
      clearInterval(this.notifTimer);
      this.notifTimer = null;
    }
    this.subscriptions.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target?.closest('.profile-menu-wrapper')) {
      this.profileMenuOpen = false;
    }
    if (!target?.closest('.mode-switcher-container')) {
      this.showModeDropdown = false;
    }
    if (!target?.closest('.cart-wrapper')) {
      this.cartDropdownOpen = false;
    }
    if (!target?.closest('.notif-wrapper')) {
      this.notifOpen = false;
    }
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
    if (!this.canUseJquery()) {
      return;
    }
    $('.menu').toggleClass('show-menu');
    $('.nav-wrapper').toggleClass('show-menu');
    const rt = window.innerWidth;
    const menuBtnX = $('.js-nav-toggle').offset()?.left || 0;
    $('.js-nav-toggle').css('right', 0);
    $('.show-menu .js-nav-toggle').css('right', -(rt - menuBtnX - 50));
  }

  private initStickyHeader(): void {
    if (!this.canUseJquery()) {
      return;
    }

    function fixedHeader() {
      const sticky = $('#header');
      const scroll = $(window).scrollTop();
      if (scroll >= 10) sticky.addClass('fixHeader');
      else sticky.removeClass('fixHeader');
    }

    $(window).on('scroll.headerSticky', fixedHeader);
    fixedHeader();
  }

  private syncIsHomeFromUrl(url: string): void {
    const clean = (url || '').split('?')[0].split('#')[0];
    this.isHome = clean === '' || clean === '/';
  }

  private getCurrentUrl(): string {
    if (typeof window !== 'undefined') {
      return `${window.location.pathname}${window.location.search}${window.location.hash}`;
    }
    return this.router.url;
  }

  private loadHeaderUserData(): void {
    if (!this.authenticated) {
      this.headerProfileImage = null;
      this.headerProfileName = null;
      return;
    }

    this.frontofficeUserService.getCurrentUser().subscribe({
      next: (user) => {
        if (!user) return;
        this.headerProfileImage = user.profilePicture ?? null;
        this.headerProfileName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim() || user.username || null;
      },
      error: () => {
        this.headerProfileImage = null;
        this.headerProfileName = null;
      },
    });
  }

  private showCheckoutMessage(type: 'success' | 'error', title: string, message: string): void {
    this.checkoutToastType = type;
    this.checkoutToastTitle = title;
    this.checkoutToastMessage = message;
    this.showCheckoutToast = true;

    if (this.checkoutToastTimer) {
      clearTimeout(this.checkoutToastTimer);
    }
    this.checkoutToastTimer = setTimeout(() => this.closeCheckoutToast(), 4000);
  }

  private handleStripeCheckoutReturn(url: string): void {
    const queryIndex = url.indexOf('?');
    if (queryIndex < 0) {
      return;
    }

    const params = new URLSearchParams(url.substring(queryIndex + 1));
    const payment = params.get('payment');

    if (payment === 'cancelled') {
      this.showCheckoutMessage(
        'error',
        'Checkout cancelled',
        'Your payment was cancelled. You can try again anytime.'
      );
      this.clearStripeQueryParams();
      return;
    }

    if (payment !== 'success') {
      return;
    }

    // Course payments are handled by CourseDetailComponent on /courses/:id.
    // Do not treat their Stripe return as a cart/pack checkout.
    const returnPath = url.split('?')[0] || '';
    if (/^\/courses\/\d+$/i.test(returnPath)) {
      return;
    }

    const sessionId = params.get('session_id');
    if (!sessionId) {
      this.showCheckoutMessage(
        'error',
        'Checkout failed',
        'Missing Stripe session id in return URL.'
      );
      this.clearStripeQueryParams();
      return;
    }

    if (this.processingStripeSessionId === sessionId || this.checkoutInProgress) {
      return;
    }

    this.processingStripeSessionId = sessionId;
    this.checkoutInProgress = true;

    this.cartService.confirmStripeCheckout(sessionId).subscribe({
      next: () => {
        this.showCheckoutMessage(
          'success',
          'Enrollment confirmed',
          'Your learning pack checkout was completed successfully.'
        );
        this.cartDropdownOpen = false;
        this.checkoutInProgress = false;
        this.processingStripeSessionId = null;
        this.cartService.refreshCart();
        this.fetchHistory();
        this.clearStripeQueryParams();
      },
      error: (err) => {
        this.checkoutInProgress = false;
        this.processingStripeSessionId = null;
        console.error('Stripe checkout confirmation failed', err);
        this.showCheckoutMessage(
          'error',
          'Checkout failed',
          err?.error?.message || err?.message || 'Payment verification failed. Please contact support.'
        );
        this.clearStripeQueryParams();
      }
    });
  }

  private clearStripeQueryParams(): void {
    const cleanPath = this.router.url.split('?')[0] || '/';
    this.router.navigateByUrl(cleanPath, { replaceUrl: true });
  }

  private initMegaMenu(): void {
    if (!this.canUseJquery()) {
      return;
    }

    const dropLinks = document.querySelectorAll('.drop-list-links');
    const dropList = document.querySelectorAll('.drop-list-tabs li');
    dropList.forEach((element: any, i: number) => {
      $(element).mouseenter(function () {
        $('.drop-list-tabs li').removeClass('active');
        $(element).addClass('active');
        $('.drop-list-links').removeClass('active');
        $(dropLinks[i]).addClass('active');
      });
    });

    $('.drop-big').mouseenter(function (this: any) {
      const allList = $(this).find('.drop-list-tabs li');
      const allListTab = $(this).find('.drop-list-links');
      $(allList).removeClass('active');
      $(allListTab).removeClass('active');
      $(allList[0]).addClass('active');
      $(allListTab[0]).addClass('active');
    });

    // Add dropdown icon class to nav items with dropdowns
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach((item: any) => {
      if (item.querySelector('.dropdown') !== null) {
        item.classList.add('dr-icon');
      }
    });
  }

  private initMobileNav(): void {
    if (!this.canUseJquery()) {
      return;
    }

    if ($(window).outerWidth() >= 990) return;

    const initElem = $('nav');
    if (!initElem.length) return;

    let curLevel = 0;
    let curItem: any = null;

    // Click handler for submenus
    initElem.on('click', '.has-dropdown > a', function (e: any) {
      e.preventDefault();
      curItem = $(e.target).closest('li');
      curLevel += 1;
      curItem.addClass('nav-dropdown-open nav-dropdown-active');
      updateMenuTitle();
      slideMenu();
    });

    // Click handler for back button
    initElem.on('click', '.nav-toggle', function () {
      if (curItem) {
        curItem.removeClass('nav-dropdown-open nav-dropdown-active');
        curItem = curItem.parent().closest('li');
        if (curItem.length) {
          curItem.addClass('nav-dropdown-open nav-dropdown-active');
        }
      }
      curLevel = curLevel > 0 ? curLevel - 1 : 0;
      updateMenuTitle();
      slideMenu();
    });

    function updateMenuTitle() {
      let title = 'Menu';
      if (curLevel > 0 && curItem && curItem.length) {
        title = curItem.children('a').text();
        initElem.find('.nav-toggle').addClass('back-visible');
      } else {
        initElem.find('.nav-toggle').removeClass('back-visible');
      }
      $('.nav-title').text(title);
    }

    function slideMenu() {
      initElem.children('ul').css({
        transform: 'translateX(-' + curLevel * 100 + '%)',
      });
    }

    updateMenuTitle();
  }

  private canUseJquery(): boolean {
    return typeof $ === 'function';
  }
}
