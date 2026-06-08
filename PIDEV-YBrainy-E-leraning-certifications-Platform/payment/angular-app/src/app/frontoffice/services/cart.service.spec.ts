import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { CartService } from './cart.service';
import { Cart, CartHistory, CartItem, CartStatus, CartAction, StripeCheckoutSession } from '../models/cart.model';

describe('CartService', () => {
  let service: CartService;
  let httpMock: HttpTestingController;

  const mockItem: CartItem = {
    id: 1, packId: 10, packTitle: 'Spring Boot Pack',
    priceAtPurchase: 49.99, quantity: 1, subtotal: 49.99,
  };

  const mockCart: Cart = {
    id: 1, userId: 42, status: CartStatus.ACTIVE, totalAmount: 49.99, items: [mockItem],
  };

  const emptyCart: Cart = {
    id: 2, userId: 42, status: CartStatus.ACTIVE, totalAmount: 0, items: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(CartService);
    httpMock = TestBed.inject(HttpTestingController);

    // Flush the automatic refreshCart() call made in constructor
    httpMock.expectOne(r => r.url.includes('/api/cart')).flush(emptyCart);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ── refreshCart ──────────────────────────────────────────────

  it('refreshCart should GET cart and update cart$', () => {
    let emitted: Cart | null = null;
    service.cart$.subscribe(c => (emitted = c));

    service.refreshCart();
    const req = httpMock.expectOne(r => r.url.includes('/api/cart') && r.method === 'GET');
    req.flush(mockCart);

    expect(emitted).toEqual(mockCart);
  });

  // ── addToCart ────────────────────────────────────────────────

  it('addToCart should POST to /cart/items and update cart$', () => {
    let emitted: Cart | null = null;
    service.cart$.subscribe(c => (emitted = c));

    service.addToCart(10, 2).subscribe();

    const req = httpMock.expectOne(r => r.url.includes('/api/cart/items') && r.method === 'POST');
    expect(req.request.body).toEqual({ packId: 10, quantity: 2 });
    req.flush(mockCart);

    expect(emitted).toEqual(mockCart);
  });

  it('addToCart defaults quantity to 1', () => {
    service.addToCart(5).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/api/cart/items'));
    expect(req.request.body).toEqual({ packId: 5, quantity: 1 });
    req.flush(mockCart);
  });

  // ── removeFromCart ───────────────────────────────────────────

  it('removeFromCart should DELETE /cart/items/:id and update cart$', () => {
    let emitted: Cart | null = null;
    service.cart$.subscribe(c => (emitted = c));

    service.removeFromCart(1).subscribe();

    const req = httpMock.expectOne(r => r.url.includes('/api/cart/items/1') && r.method === 'DELETE');
    req.flush(emptyCart);

    expect(emitted).toEqual(emptyCart);
  });

  // ── clearCart ────────────────────────────────────────────────

  it('clearCart should DELETE /cart and update cart$', () => {
    service.clearCart().subscribe();
    const req = httpMock.expectOne(r => r.url.match(/\/api\/cart$/) && r.method === 'DELETE');
    req.flush(emptyCart);
    expect(req.request.method).toBe('DELETE');
  });

  // ── checkout ─────────────────────────────────────────────────

  it('checkout should POST to /cart/checkout', () => {
    service.checkout().subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/api/cart/checkout') && r.method === 'POST');
    expect(req.request.body).toEqual({});
    req.flush(emptyCart);
  });

  // ── createStripeCheckoutSession ──────────────────────────────

  it('createStripeCheckoutSession should POST to stripe-session endpoint', () => {
    const mockSession: StripeCheckoutSession = {
      sessionId: 'sess_123', checkoutUrl: 'https://stripe.com/pay', publishableKey: 'pk_test',
    };

    service.createStripeCheckoutSession().subscribe(s => {
      expect(s.sessionId).toBe('sess_123');
      expect(s.checkoutUrl).toBe('https://stripe.com/pay');
    });

    const req = httpMock.expectOne(r =>
      r.url.includes('/api/cart/checkout/stripe-session') && r.method === 'POST'
    );
    req.flush(mockSession);
  });

  // ── confirmStripeCheckout ────────────────────────────────────

  it('confirmStripeCheckout should POST sessionId to stripe-confirm', () => {
    service.confirmStripeCheckout('sess_abc').subscribe();

    const req = httpMock.expectOne(r =>
      r.url.includes('/api/cart/checkout/stripe-confirm') && r.method === 'POST'
    );
    expect(req.request.body).toEqual({ sessionId: 'sess_abc' });
    req.flush(mockCart);
  });

  // ── getHistory ───────────────────────────────────────────────

  it('getHistory should GET /cart/history', () => {
    const mockHistory: CartHistory[] = [
      {
        id: 1, cartId: 1, action: CartAction.ADD_ITEM, packTitle: 'Spring Boot Pack',
        quantity: 1, totalAmount: 49.99, cartStatus: CartStatus.ACTIVE,
        description: 'Added item', createdAt: '2025-01-01T00:00:00',
      },
    ];

    service.getHistory().subscribe(h => {
      expect(h.length).toBe(1);
      expect(h[0].action).toBe(CartAction.ADD_ITEM);
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/cart/history') && r.method === 'GET');
    req.flush(mockHistory);
  });

  // ── getCartItemCount ─────────────────────────────────────────

  it('getCartItemCount returns 0 when cart is null', () => {
    (service as any).cartSubject.next(null);
    expect(service.getCartItemCount()).toBe(0);
  });

  it('getCartItemCount sums item quantities', () => {
    const multiCart: Cart = {
      ...mockCart,
      items: [
        { ...mockItem, quantity: 2 },
        { ...mockItem, id: 2, quantity: 3 },
      ],
    };
    (service as any).cartSubject.next(multiCart);
    expect(service.getCartItemCount()).toBe(5);
  });
});
