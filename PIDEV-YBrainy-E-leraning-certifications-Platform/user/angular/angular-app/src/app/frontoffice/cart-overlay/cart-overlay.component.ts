import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Cart, CartAction, CartHistory, CartItem, CartStatus } from '../models/cart.model';
import { CartService } from '../services/cart.service';
import { CartOverlayService, CartOverlayTab } from '../services/cart-overlay.service';

@Component({
  selector: 'app-cart-overlay',
  standalone: false,
  templateUrl: './cart-overlay.component.html',
  styleUrls: ['./cart-overlay.component.css']
})
export class CartOverlayComponent implements OnInit, OnDestroy {
  readonly particles = Array.from({ length: 16 }, (_, index) => index + 1);

  isOpen = false;
  activeTab: CartOverlayTab = 'cart';
  justAddedPackTitle: string | null = null;

  cart: Cart | null = null;
  history: CartHistory[] = [];

  cartLoading = false;
  historyLoading = false;
  cartError = '';
  historyError = '';
  removingItemId: number | null = null;
  clearing = false;
  checkoutInProgress = false;

  private readonly subscriptions = new Subscription();
  private previousBodyOverflow = '';

  constructor(
    private readonly cartService: CartService,
    private readonly cartOverlay: CartOverlayService
  ) {}

  ngOnInit(): void {
    this.subscriptions.add(
      this.cartService.cart$.subscribe((cart) => {
        this.cart = cart;
      })
    );

    this.subscriptions.add(
      this.cartOverlay.state$.subscribe((state) => {
        const wasOpen = this.isOpen;
        const previousTab = this.activeTab;

        this.isOpen = state.open;
        this.activeTab = state.activeTab;
        this.justAddedPackTitle = state.justAddedPackTitle;

        this.syncBodyScroll();

        if (state.open && !wasOpen) {
          this.loadCart();
          this.loadHistory();
          return;
        }

        if (state.open && previousTab !== state.activeTab) {
          if (state.activeTab === 'cart') {
            this.loadCart();
          } else {
            this.loadHistory();
          }
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.restoreBodyScroll();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen) {
      this.close();
    }
  }

  get hasItems(): boolean {
    return (this.cart?.items?.length ?? 0) > 0;
  }

  get totalItems(): number {
    return (this.cart?.items ?? []).reduce((sum, item) => sum + item.quantity, 0);
  }

  get totalAmount(): number {
    return this.cart?.totalAmount ?? 0;
  }

  get checkoutCount(): number {
    return this.history.filter((record) => record.action === CartAction.CHECKOUT).length;
  }

  close(): void {
    this.cartOverlay.close();
  }

  switchTab(tab: CartOverlayTab): void {
    this.cartOverlay.switchTab(tab);
  }

  loadCart(): void {
    this.cartLoading = true;
    this.cartError = '';

    this.cartService.loadCart().subscribe({
      next: (cart) => {
        this.cart = cart;
        this.cartLoading = false;
      },
      error: (err) => {
        this.cartLoading = false;
        if (err?.status === 404) {
          this.cart = null;
          return;
        }
        this.cartError = err?.error?.message || 'Unable to load your cart right now.';
      }
    });
  }

  loadHistory(): void {
    this.historyLoading = true;
    this.historyError = '';

    this.cartService.getHistory().subscribe({
      next: (history) => {
        this.history = [...(history ?? [])].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        this.historyLoading = false;
      },
      error: (err) => {
        this.historyError = err?.error?.message || 'Unable to load your cart history right now.';
        this.historyLoading = false;
      }
    });
  }

  removeItem(item: CartItem): void {
    this.cartError = '';
    this.removingItemId = item.id;

    this.cartService.removeFromCart(item.id).subscribe({
      next: (cart) => {
        this.cart = cart;
        this.removingItemId = null;
      },
      error: (err) => {
        this.cartError = err?.error?.message || 'Failed to remove this pack from your cart.';
        this.removingItemId = null;
      }
    });
  }

  clearCart(): void {
    this.cartError = '';
    this.clearing = true;

    this.cartService.clearCart().subscribe({
      next: (cart) => {
        this.cart = cart;
        this.clearing = false;
      },
      error: (err) => {
        this.cartError = err?.error?.message || 'Failed to clear your cart.';
        this.clearing = false;
      }
    });
  }

  startCheckout(): void {
    if (!this.hasItems || this.checkoutInProgress) {
      return;
    }

    this.cartError = '';
    this.checkoutInProgress = true;

    this.cartService.createStripeCheckoutSession().subscribe({
      next: (session) => {
        if (!session?.checkoutUrl) {
          this.checkoutInProgress = false;
          this.cartError = 'Stripe checkout URL is missing. Please try again.';
          return;
        }

        window.location.href = session.checkoutUrl;
      },
      error: (err) => {
        this.checkoutInProgress = false;
        this.cartError = err?.error?.message || err?.message || 'Unable to start checkout right now.';
      }
    });
  }

  getActionLabel(action: CartAction): string {
    switch (action) {
      case CartAction.ADD_ITEM:
        return 'Added';
      case CartAction.REMOVE_ITEM:
        return 'Removed';
      case CartAction.CLEAR_CART:
        return 'Cleared';
      case CartAction.CHECKOUT:
        return 'Checked Out';
      case CartAction.CART_CREATED:
      default:
        return 'Created';
    }
  }

  getActionTone(action: CartAction): string {
    switch (action) {
      case CartAction.ADD_ITEM:
        return 'tone-add';
      case CartAction.REMOVE_ITEM:
        return 'tone-remove';
      case CartAction.CHECKOUT:
        return 'tone-checkout';
      case CartAction.CLEAR_CART:
        return 'tone-clear';
      case CartAction.CART_CREATED:
      default:
        return 'tone-created';
    }
  }

  getStatusLabel(status: CartStatus | undefined): string {
    switch (status) {
      case CartStatus.CHECKED_OUT:
        return 'Checked Out';
      case CartStatus.CANCELLED:
        return 'Cancelled';
      case CartStatus.ACTIVE:
      default:
        return 'Active';
    }
  }

  trackHistory(_: number, record: CartHistory): number {
    return record.id;
  }

  private syncBodyScroll(): void {
    if (typeof document === 'undefined') {
      return;
    }

    if (this.isOpen) {
      if (!this.previousBodyOverflow) {
        this.previousBodyOverflow = document.body.style.overflow;
      }
      document.body.style.overflow = 'hidden';
      return;
    }

    this.restoreBodyScroll();
  }

  private restoreBodyScroll(): void {
    if (typeof document === 'undefined') {
      return;
    }

    document.body.style.overflow = this.previousBodyOverflow;
    this.previousBodyOverflow = '';
  }
}
