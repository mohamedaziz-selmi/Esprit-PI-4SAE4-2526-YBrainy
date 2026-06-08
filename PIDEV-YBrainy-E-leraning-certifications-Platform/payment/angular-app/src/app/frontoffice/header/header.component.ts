import { Component, AfterViewInit, OnDestroy, ElementRef } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { CartService } from '../services/cart.service';

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
  isHome = true;
  menuOpen = false;
  cartDropdownOpen = false;
  showHistory = false;
  cartHistory: any[] = [];
  checkoutInProgress = false;
  showCheckoutToast = false;
  checkoutToastType: 'success' | 'error' = 'success';
  checkoutToastTitle = '';
  checkoutToastMessage = '';
  private mobileNav: any;
  private readonly subscriptions = new Subscription();
  private checkoutToastTimer: ReturnType<typeof setTimeout> | null = null;
  private processingStripeSessionId: string | null = null;

  constructor(
    private el: ElementRef,
    private router: Router,
    public cartService: CartService
  ) { }

  ngAfterViewInit(): void {
    this.syncIsHomeFromUrl(this.router.url);
    this.handleStripeCheckoutReturn(this.router.url);
    this.subscriptions.add(
      this.router.events
        .pipe(
          filter((e): e is NavigationEnd => e instanceof NavigationEnd)
        )
        .subscribe((e) => {
          this.syncIsHomeFromUrl(e.urlAfterRedirects);
          this.handleStripeCheckoutReturn(e.urlAfterRedirects);
        })
    );

    this.initStickyHeader();
    this.initMegaMenu();
    this.initMobileNav();

    // Auto-refresh history when cart changes
    this.subscriptions.add(
      this.cartService.cart$.subscribe(() => {
        if (this.cartDropdownOpen) {
          this.fetchHistory();
        }
      })
    );
  }

  ngOnDestroy(): void {
    $(window).off('scroll.headerSticky');
    if (this.checkoutToastTimer) {
      clearTimeout(this.checkoutToastTimer);
      this.checkoutToastTimer = null;
    }
    this.subscriptions.unsubscribe();
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
    $('.menu').toggleClass('show-menu');
    $('.nav-wrapper').toggleClass('show-menu');
    const rt = window.innerWidth;
    const menuBtnX = $('.js-nav-toggle').offset()?.left || 0;
    $('.js-nav-toggle').css('right', 0);
    $('.show-menu .js-nav-toggle').css('right', -(rt - menuBtnX - 50));
  }

  toggleCartDropdown(): void {
    this.cartDropdownOpen = !this.cartDropdownOpen;
    if (this.cartDropdownOpen) {
      this.fetchHistory();
    }
  }

  toggleHistory(show: boolean): void {
    this.showHistory = show;
    if (show) {
      this.fetchHistory();
    }
  }

  fetchHistory(): void {
    this.cartService.getHistory().subscribe({
      next: (history) => this.cartHistory = history.reverse(), // Newest first
      error: (err) => console.error('Error fetching history', err)
    });
  }

  removeFromCart(itemId: number, event: Event): void {
    event.stopPropagation();
    this.cartService.removeFromCart(itemId).subscribe({
      next: () => this.fetchHistory()
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
          err.error?.message || err.message || 'We could not complete checkout. Please try again.'
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
          err.error?.message || err.message || 'Payment verification failed. Please contact support.'
        );
        this.clearStripeQueryParams();
      }
    });
  }

  private clearStripeQueryParams(): void {
    const cleanPath = this.router.url.split('?')[0] || '/';
    this.router.navigateByUrl(cleanPath, { replaceUrl: true });
  }

  private initStickyHeader(): void {
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

  private initMegaMenu(): void {
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
}
