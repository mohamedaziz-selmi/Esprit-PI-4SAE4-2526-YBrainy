import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { CategoryResponse, ThreadInteractionStatus, ThreadResponse, VoteType, ReactionType, UserProfileResponse } from '../../models/forum.models';
import { CategoryApiService } from '../../services/category-api.service';
import { PostApiService } from '../../services/post-api.service';
import { ThreadApiService } from '../../services/thread-api.service';
import { ThreadInteractionService } from '../../services/thread-interaction.service';
import { MediaUrlService } from '../../services/media-url.service';
import { UserStateService } from '../../services/user-state.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-forum',
  standalone: false,
  templateUrl: './forum.component.html',
  styleUrls: ['./forum.component.css'],
  host: { 'style': 'display:block' }
})
export class ForumComponent implements OnInit, OnDestroy {
  threads: ThreadResponse[] = [];
  loading = true;
  error: string | null = null;

  categories: CategoryResponse[] = [];
  selectedCategoryId: number | null = null;

  searchQuery = '';
  selectedStatus: 'ALL' | 'OPEN' | 'CLOSED' | 'LOCKED' = 'ALL';
  selectedSort: 'hot' | 'top' | 'new' = 'hot';

  pageIndex = 1;
  pageSize = 10;

  postCountByThreadId: Record<number, number> = {};
  interactionByThreadId: Record<number, ThreadInteractionStatus> = {};

  // ── Sidebar ──────────────────────────────────────────────
  profile: UserProfileResponse | null = null;
  showAddCategory = false;
  addCategoryForm: FormGroup;
  addCategoryLoading = false;
  addCategoryError: string | null = null;

  private readonly subs = new Subscription();

  constructor(
    private threadApi: ThreadApiService,
    private categoryApi: CategoryApiService,
    private postApi: PostApiService,
    private interactionApi: ThreadInteractionService,
    private userState: UserStateService,
    private fb: FormBuilder,
    private router: Router,
    public media: MediaUrlService,
    public auth: AuthService
  ) {
    this.addCategoryForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      description: ['', [Validators.maxLength(300)]]
    });
  }

  get userInitials(): string {
    const name = this.profile?.username ?? 'U';
    return name.slice(0, 2).toUpperCase();
  }

  countByCategory(categoryId: number): number {
    return this.threads.filter(t => t.category?.id === categoryId).length;
  }

  get recommendedThreads(): ThreadResponse[] {
    return [...this.threads]
      .sort((a, b) => {
        const scoreDiff = (b.voteScore ?? 0) - (a.voteScore ?? 0);
        if (scoreDiff !== 0) return scoreDiff;
        const upvoteDiff = (b.upvoteCount ?? 0) - (a.upvoteCount ?? 0);
        if (upvoteDiff !== 0) return upvoteDiff;
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      })
      .slice(0, 5);
  }

  get openThreadsCount(): number {
    return this.threads.filter(t => t.status === 'OPEN').length;
  }

  get interactedThreadsCount(): number {
    return Object.keys(this.interactionByThreadId ?? {}).length;
  }

  toggleAddCategory(): void {
    this.showAddCategory = !this.showAddCategory;
    if (!this.showAddCategory) {
      this.addCategoryForm.reset();
      this.addCategoryError = null;
    }
  }

  submitAddCategory(): void {
    if (this.addCategoryForm.invalid) {
      this.addCategoryForm.markAllAsTouched();
      return;
    }
    this.addCategoryLoading = true;
    this.addCategoryError = null;
    const { name, description } = this.addCategoryForm.value;
    this.subs.add(
      this.categoryApi.create({ name: name.trim(), description: description?.trim() || null }).subscribe({
        next: () => {
          this.addCategoryLoading = false;
          this.addCategoryForm.reset();
          this.showAddCategory = false;
          this.fetchCategories();
        },
        error: (err) => {
          this.addCategoryLoading = false;
          this.addCategoryError = err?.error?.message ?? 'Failed to create category';
        }
      })
    );
  }

  refreshPostCounts(): void {
    const visible = this.pagedThreads;
    if (!visible.length) {
      this.postCountByThreadId = {};
      return;
    }
    for (const t of visible) {
      const threadId = t.id;
      if (this.postCountByThreadId[threadId] !== undefined) continue;
      this.subs.add(
        this.postApi.countByThread(threadId).subscribe({
          next: (count) => {
            this.postCountByThreadId = { ...this.postCountByThreadId, [threadId]: count };
          },
          error: () => {
            this.postCountByThreadId = { ...this.postCountByThreadId, [threadId]: 0 };
          },
        })
      );
    }
  }

  loadInteractions(): void {
    const userId = this.userState.currentUserId;
    // Pre-populate with thread-level counts so they display immediately
    const map: Record<number, ThreadInteractionStatus> = { ...this.interactionByThreadId };
    for (const t of this.threads) {
      if (!map[t.id]) {
        map[t.id] = {
          userVote: null, userReaction: null, wishlisted: false,
          upvoteCount: t.upvoteCount ?? 0,
          downvoteCount: t.downvoteCount ?? 0,
          voteScore: t.voteScore ?? 0,
          likeCount: t.likeCount ?? 0,
          dislikeCount: t.dislikeCount ?? 0
        };
      }
    }
    this.interactionByThreadId = map;
    // Then load user-specific state (userVote / userReaction / wishlisted)
    for (const t of this.threads) {
      this.subs.add(
        this.interactionApi.getStatus(t.id, userId).subscribe({
          next: (status) => {
            this.interactionByThreadId = { ...this.interactionByThreadId, [t.id]: status };
          },
          error: () => {}
        })
      );
    }
  }

  getInteraction(threadId: number): ThreadInteractionStatus {
    return this.interactionByThreadId[threadId] ?? {
      userVote: null, userReaction: null, wishlisted: false,
      upvoteCount: 0, downvoteCount: 0, voteScore: 0, likeCount: 0, dislikeCount: 0
    };
  }

  /** True if the logged-in user is the author of this thread */
  isOwnThread(t: ThreadResponse): boolean {
    const uid = this.auth.currentUserId;
    return uid !== null && t.author?.id === uid;
  }

  onVote(event: Event, t: ThreadResponse, voteType: VoteType): void {
    event.stopPropagation();
    const userId = this.userState.currentUserId;
    this.subs.add(
      this.interactionApi.vote(t.id, userId, voteType).subscribe({
        next: (status) => {
          this.interactionByThreadId = { ...this.interactionByThreadId, [t.id]: status };
          const idx = this.threads.findIndex(x => x.id === t.id);
          if (idx !== -1) {
            this.threads[idx] = { ...this.threads[idx], voteScore: status.voteScore, upvoteCount: status.upvoteCount, downvoteCount: status.downvoteCount };
          }
        },
        error: () => {}
      })
    );
  }

  onReact(event: Event, t: ThreadResponse, reactionType: ReactionType): void {
    event.stopPropagation();
    const userId = this.userState.currentUserId;
    this.subs.add(
      this.interactionApi.react(t.id, userId, reactionType).subscribe({
        next: (status) => {
          this.interactionByThreadId = { ...this.interactionByThreadId, [t.id]: status };
          const idx = this.threads.findIndex(x => x.id === t.id);
          if (idx !== -1) {
            this.threads[idx] = { ...this.threads[idx], likeCount: status.likeCount, dislikeCount: status.dislikeCount };
          }
        },
        error: () => {}
      })
    );
  }

  onToggleWishlist(event: Event, t: ThreadResponse): void {
    event.stopPropagation();
    const userId = this.userState.currentUserId;
    this.subs.add(
      this.interactionApi.toggleWishlist(t.id, userId).subscribe({
        next: (status) => {
          this.interactionByThreadId = { ...this.interactionByThreadId, [t.id]: status };
        },
        error: () => {}
      })
    );
  }

  ngOnInit(): void {
    this.subs.add(this.userState.profile$.subscribe(p => { this.profile = p; }));
    this.userState.refresh();
    this.fetchCategories();
    this.fetchThreads();
  }

  fetchCategories(): void {
    this.subs.add(
      this.categoryApi.list().subscribe({
        next: (cats) => { this.categories = cats; },
        error: () => { this.categories = []; },
      })
    );
  }

  onCategoryFilterChange(raw: string): void {
    const v = raw === '' ? null : Number(raw);
    this.selectedCategoryId = Number.isFinite(v as number) ? (v as number) : null;
    this.pageIndex = 1;
    this.refreshPostCounts();
  }

  onSearchChange(raw: string): void {
    this.searchQuery = raw;
    this.pageIndex = 1;
    this.refreshPostCounts();
  }

  onStatusChange(raw: string): void {
    const v = (raw || 'ALL').toUpperCase();
    this.selectedStatus = (v === 'OPEN' || v === 'CLOSED' || v === 'LOCKED' ? v : 'ALL') as any;
    this.pageIndex = 1;
    this.refreshPostCounts();
  }

  onSortChange(sort: 'hot' | 'top' | 'new'): void {
    this.selectedSort = sort;
    this.pageIndex = 1;
  }

  onPageSizeChange(raw: string): void {
    const v = Number(raw);
    this.pageSize = Number.isFinite(v) && v > 0 ? v : 10;
    this.pageIndex = 1;
    this.refreshPostCounts();
  }

  prevPage(): void {
    this.pageIndex = Math.max(1, this.pageIndex - 1);
    this.refreshPostCounts();
  }

  nextPage(): void {
    this.pageIndex = Math.min(this.totalPages, this.pageIndex + 1);
    this.refreshPostCounts();
  }

  goToPage(i: number): void {
    if (!Number.isFinite(i)) return;
    this.pageIndex = Math.min(this.totalPages, Math.max(1, i));
    this.refreshPostCounts();
  }

  getPostCount(threadId: number): number {
    return this.postCountByThreadId[threadId] ?? 0;
  }

  get filteredThreads(): ThreadResponse[] {
    const q = this.searchQuery.trim().toLowerCase();
    const uid = this.auth.currentUserId;
    let items = this.threads.filter(t => t.status !== 'CLOSED' || t.author?.id === uid);

    if (this.selectedCategoryId !== null) {
      items = items.filter((t) => (t.category?.id ?? null) === this.selectedCategoryId);
    }

    if (this.selectedStatus !== 'ALL') {
      items = items.filter((t) => t.status === this.selectedStatus);
    }

    if (q) {
      items = items.filter((t) => {
        const title = (t.title ?? '').toLowerCase();
        const body = (t.body ?? '').toLowerCase();
        const author = (t.author?.username ?? '').toLowerCase();
        const cat = (t.category?.name ?? '').toLowerCase();
        return title.includes(q) || body.includes(q) || author.includes(q) || cat.includes(q);
      });
    }

    if (this.selectedSort === 'new') {
      items = items.slice().sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
    } else if (this.selectedSort === 'top') {
      items = items.slice().sort((a, b) => (b.upvoteCount ?? 0) - (a.upvoteCount ?? 0));
    }

    return items;
  }

  get totalPages(): number {
    const total = this.filteredThreads.length;
    return Math.max(1, Math.ceil(total / this.pageSize));
  }

  get pagedThreads(): ThreadResponse[] {
    const start = (this.pageIndex - 1) * this.pageSize;
    return this.filteredThreads.slice(start, start + this.pageSize);
  }

  fetchThreads(): void {
    this.loading = true;
    this.error = null;

    this.subs.add(
      this.threadApi.list().subscribe({
        next: (threads) => {
          this.threads = threads;
          this.loading = false;
          this.pageIndex = 1;
          this.refreshPostCounts();
          this.loadInteractions();
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.message ?? 'Failed to load threads';
        },
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
