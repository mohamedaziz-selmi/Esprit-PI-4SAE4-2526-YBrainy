import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cart, CartHistory, StripeCheckoutSession } from '../models/cart.model';
import { isAuthenticated } from '../../auth/keycloak.service';

@Injectable({
    providedIn: 'root'
})
export class CartService {
    private apiUrl = `${environment.cartApiUrl}/cart`;
    private cartSubject = new BehaviorSubject<Cart | null>(null);
    cart$ = this.cartSubject.asObservable();

    constructor(private http: HttpClient) {
        if (isAuthenticated()) {
            this.refreshCart();
        }
    }

    loadCart(): Observable<Cart> {
        return this.http.get<Cart>(this.apiUrl).pipe(
            tap((cart) => this.cartSubject.next(cart))
        );
    }

    refreshCart(): void {
        if (!isAuthenticated()) {
            this.cartSubject.next(null);
            return;
        }

        this.loadCart().subscribe({
            next: (cart) => this.cartSubject.next(cart),
            error: (err) => {
                if (err?.status === 404) {
                    this.cartSubject.next(null);
                    return;
                }
                console.error('Error fetching cart', err);
            }
        });
    }

    addToCart(packId: number, quantity: number = 1): Observable<Cart> {
        return this.http.post<Cart>(`${this.apiUrl}/items`, { packId, quantity }).pipe(
            tap(cart => this.cartSubject.next(cart))
        );
    }

    removeFromCart(itemId: number): Observable<Cart> {
        return this.http.delete<Cart>(`${this.apiUrl}/items/${itemId}`).pipe(
            tap(cart => this.cartSubject.next(cart))
        );
    }

    clearCart(): Observable<Cart> {
        return this.http.delete<Cart>(this.apiUrl).pipe(
            tap(cart => this.cartSubject.next(cart))
        );
    }

    checkout(): Observable<Cart> {
        return this.http.post<Cart>(`${this.apiUrl}/checkout`, {}).pipe(
            tap(cart => this.cartSubject.next(cart))
        );
    }

    createStripeCheckoutSession(): Observable<StripeCheckoutSession> {
        return this.http.post<StripeCheckoutSession>(`${this.apiUrl}/checkout/stripe-session`, {});
    }

    confirmStripeCheckout(sessionId: string): Observable<Cart> {
        return this.http.post<Cart>(`${this.apiUrl}/checkout/stripe-confirm`, { sessionId }).pipe(
            tap(cart => this.cartSubject.next(cart))
        );
    }

    getHistory(): Observable<CartHistory[]> {
        return this.http.get<CartHistory[]>(`${this.apiUrl}/history`);
    }

    getCartItemCount(): number {
        const cart = this.cartSubject.value;
        return cart ? cart.items.reduce((acc, item) => acc + item.quantity, 0) : 0;
    }
}
