import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CartHistoryService } from '../../services/cart-history.service';
import { CartHistoryRecord, PackOrderRow } from '../../models/cart-history.model';
import { RuntimePageStyleService } from '../runtime-page-style.service';

@Component({
  selector: 'app-packs-order',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './packs-order.component.html',
  styleUrl: './packs-order.component.css',
  encapsulation: ViewEncapsulation.None,
  host: { style: 'display:block' }
})
export class PacksOrderComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly detachPageStyles: () => void;

  loading = false;
  error = '';
  searchTerm = '';

  orderRows: PackOrderRow[] = [];
  filteredOrderRows: PackOrderRow[] = [];
  totalOrderItems = 0;
  completedItems = 0;
  checkedOutOrders = 0;
  totalRevenue = 0;
  mostBoughtPackTitle = 'No pack purchases yet';
  mostBoughtPackQuantity = 0;
  mostBoughtPackBasedOnCheckedOut = true;

  constructor(
    private cartHistoryService: CartHistoryService,
    private pageStyles: RuntimePageStyleService
  ) {
    this.detachPageStyles = this.pageStyles.attach(['assets/backoffice/pages/packs-order-page.css']);
  }

  ngOnInit(): void {
    this.loadOrderHistory();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.loadScripts(), 100);
  }

  ngOnDestroy(): void {
    this.detachPageStyles();
  }

  loadOrderHistory(): void {
    this.loading = true;
    this.error = '';

    this.cartHistoryService.getHistory().subscribe({
      next: (history) => {
        this.mapHistoryToOrderRows(history || []);
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || 'Failed to load cart history.';
        this.loading = false;
      }
    });
  }

  private mapHistoryToOrderRows(history: CartHistoryRecord[]): void {
    const checkedOutCartIds = new Set<number>(
      history
        .filter(h => h.action === 'CHECKOUT' || h.cartStatus === 'CHECKED_OUT')
        .map(h => h.cartId)
    );

    const checkoutRows = history.filter(h => h.action === 'CHECKOUT');
    this.totalRevenue = checkoutRows.reduce((sum, row) => sum + (row.totalAmount || 0), 0);
    this.checkedOutOrders = new Set(checkoutRows.map(r => r.cartId)).size;

    this.orderRows = history
      .filter(h => h.action === 'ADD_ITEM' && !!h.packTitle)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .map(h => {
        const checkedOut = checkedOutCartIds.has(h.cartId);
        return {
          ...h,
          completed: checkedOut,
          checkedOut
        };
      });

    this.totalOrderItems = this.orderRows.reduce((sum, row) => sum + (row.quantity || 0), 0);
    this.completedItems = this.orderRows.filter(row => row.completed).length;
    this.applySearchFilter();
    this.computeMostBoughtPack();
  }

  private computeMostBoughtPack(): void {
    const checkedOutRows = this.orderRows.filter(row => row.checkedOut);
    const sourceRows = checkedOutRows.length > 0 ? checkedOutRows : this.orderRows;
    this.mostBoughtPackBasedOnCheckedOut = checkedOutRows.length > 0;

    if (sourceRows.length === 0) {
      this.mostBoughtPackTitle = 'No pack purchases yet';
      this.mostBoughtPackQuantity = 0;
      return;
    }

    const quantities = new Map<string, number>();
    for (const row of sourceRows) {
      const title = row.packTitle || 'Unknown Pack';
      const qty = row.quantity || 0;
      quantities.set(title, (quantities.get(title) || 0) + qty);
    }

    let topTitle = 'No pack purchases yet';
    let topQty = 0;
    quantities.forEach((qty, title) => {
      if (qty > topQty) {
        topQty = qty;
        topTitle = title;
      }
    });

    this.mostBoughtPackTitle = topTitle;
    this.mostBoughtPackQuantity = topQty;
  }

  private loadScripts(): void {
    const scripts = [
      'assets/backoffice/vendor/global/global.min.js',
      'assets/backoffice/js/custom.js',
      'assets/backoffice/js/dlabnav-init.js',
      'assets/backoffice/js/demo.js'
    ];
    this.loadScriptSequence(scripts);
  }

  private loadScriptSequence(scripts: string[], index = 0): void {
    if (index >= scripts.length) return;
    const script = document.createElement('script');
    script.src = scripts[index];
    script.async = false;
    script.onload = () => this.loadScriptSequence(scripts, index + 1);
    script.onerror = () => this.loadScriptSequence(scripts, index + 1);
    document.body.appendChild(script);
  }

  getActionLabel(action: string): string {
    return action.replaceAll('_', ' ');
  }

  onSearchChange(): void {
    this.applySearchFilter();
  }

  private applySearchFilter(): void {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) {
      this.filteredOrderRows = [...this.orderRows];
      return;
    }

    this.filteredOrderRows = this.orderRows.filter(row =>
      String(row.cartId).includes(term) ||
      (row.packTitle || '').toLowerCase().includes(term) ||
      row.action.toLowerCase().includes(term) ||
      row.createdAt.toLowerCase().includes(term)
    );
  }
}
