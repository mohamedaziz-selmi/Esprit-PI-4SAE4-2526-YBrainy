import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type CartOverlayTab = 'cart' | 'history';

export interface CartOverlayState {
  open: boolean;
  activeTab: CartOverlayTab;
  justAddedPackTitle: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class CartOverlayService {
  private readonly stateSubject = new BehaviorSubject<CartOverlayState>({
    open: false,
    activeTab: 'cart',
    justAddedPackTitle: null
  });

  readonly state$ = this.stateSubject.asObservable();

  openCart(justAddedPackTitle: string | null = null): void {
    this.stateSubject.next({
      open: true,
      activeTab: 'cart',
      justAddedPackTitle
    });
  }

  openHistory(): void {
    this.stateSubject.next({
      open: true,
      activeTab: 'history',
      justAddedPackTitle: null
    });
  }

  switchTab(activeTab: CartOverlayTab): void {
    const current = this.stateSubject.value;
    this.stateSubject.next({
      ...current,
      open: true,
      activeTab,
      justAddedPackTitle: activeTab === 'cart' ? current.justAddedPackTitle : null
    });
  }

  close(): void {
    const current = this.stateSubject.value;
    this.stateSubject.next({
      ...current,
      open: false,
      justAddedPackTitle: null
    });
  }
}
