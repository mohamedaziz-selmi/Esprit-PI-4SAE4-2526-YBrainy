import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PackService } from '../../../../services/pack.service';
import { Pack } from '../../../../models/pack.model';
import { CartService } from '../../../services/cart.service';
import { CartOverlayService } from '../../../services/cart-overlay.service';
import { isAuthenticated, redirectToAppLogin } from '../../../../auth/keycloak.service';

@Component({
  selector: 'app-fo-pack-detail',
  standalone: false,
  templateUrl: './pack-detail.component.html',
  styleUrls: ['./pack-detail.component.css'],
  host: { style: 'display:block' }
})
export class FoPackDetailComponent implements OnInit {
  pack: Pack | null = null;
  loading = true;
  addingToCart = false;
  error = '';
  cartError = '';

  constructor(
    private route: ActivatedRoute,
    private packService: PackService,
    private cartService: CartService,
    private cartOverlay: CartOverlayService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadPack(+id);
    }
  }

  loadPack(id: number): void {
    this.loading = true;
    this.packService.getActivePackById(id).subscribe({
      next: (data) => {
        this.pack = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'Pack not found or not available';
        this.loading = false;
      }
    });
  }

  addToCart(): void {
    if (!this.pack) return;
    if (!isAuthenticated()) {
      redirectToAppLogin(window.location.href);
      return;
    }

    this.addingToCart = true;
    this.cartError = '';

    this.cartService.addToCart(this.pack.id).subscribe({
      next: () => {
        this.addingToCart = false;
        this.cartOverlay.openCart(this.pack?.title || null);
      },
      error: (err) => {
        this.addingToCart = false;
        if (err?.status === 401) {
          redirectToAppLogin(window.location.href);
          return;
        }
        console.error('Add to cart failed', err);
        this.cartError = err?.error?.message || err?.message || 'Failed to add this pack to your cart.';
      }
    });
  }

  getDiscount(): number {
    if (!this.pack || !this.pack.originalPrice || this.pack.originalPrice === 0) return 0;
    return Math.round(((this.pack.originalPrice - this.pack.salePrice) / this.pack.originalPrice) * 100);
  }

  getLevelLabel(level: string): string {
    switch (level) {
      case 'BEGINNER':
        return 'Beginner';
      case 'INTERMEDIATE':
        return 'Intermediate';
      case 'ADVANCED':
        return 'Advanced';
      default:
        return level;
    }
  }
}
