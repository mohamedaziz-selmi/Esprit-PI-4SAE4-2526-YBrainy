import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ThreadResponse } from '../../models/forum.models';
import { ThreadInteractionService } from '../../services/thread-interaction.service';
import { UserStateService } from '../../services/user-state.service';
import { MediaUrlService } from '../../services/media-url.service';

@Component({
  selector: 'app-wishlist',
  standalone: false,
  templateUrl: './wishlist.component.html',
  styleUrls: ['./wishlist.component.css'],
  host: { style: 'display:block' }
})
export class WishlistComponent implements OnInit, OnDestroy {
  threads: ThreadResponse[] = [];
  loading = true;
  error: string | null = null;

  private readonly subs = new Subscription();

  constructor(
    private interactionApi: ThreadInteractionService,
    private userState: UserStateService,
    private router: Router,
    public media: MediaUrlService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    const userId = this.userState.currentUserId;
    this.subs.add(
      this.interactionApi.getWishlist(userId).subscribe({
        next: (threads) => {
          this.threads = threads;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.message ?? 'Failed to load wishlist';
        }
      })
    );
  }

  removeFromWishlist(t: ThreadResponse): void {
    const userId = this.userState.currentUserId;
    this.subs.add(
      this.interactionApi.toggleWishlist(t.id, userId).subscribe({
        next: () => {
          this.threads = this.threads.filter(x => x.id !== t.id);
        },
        error: () => {}
      })
    );
  }

  goToThread(threadId: number): void {
    this.router.navigate(['/forum', threadId]);
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }
}
