import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { forkJoin, Subscription } from 'rxjs';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { CommentResponse, PostResponse, ThreadInteractionStatus, ThreadResponse, VoteType, ReactionType } from '../../../models/forum.models';
import { CommentApiService } from '../../../services/comment-api.service';
import { PostApiService } from '../../../services/post-api.service';
import { ThreadApiService } from '../../../services/thread-api.service';
import { ThreadInteractionService } from '../../../services/thread-interaction.service';
import { UserStateService } from '../../../services/user-state.service';
import { MediaUrlService } from '../../../services/media-url.service';
import { AiGenerationService } from '../../../services/ai-generation.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-thread-detail',
  standalone: false,
  templateUrl: './thread-detail.component.html',
  styleUrls: ['./thread-detail.component.css'],
  host: { style: 'display:block' },
})
export class ThreadDetailComponent implements OnInit, OnDestroy {
  threadId: number | null = null;

  thread: ThreadResponse | null = null;
  posts: PostResponse[] = [];
  commentsByPostId: Record<number, CommentResponse[]> = {};

  loading = true;
  error: string | null = null;

  submittingPost = false;
  postSubmitAttempted = false;

  generatingPost = false;
  postAiError: string | null = null;

  postForm: FormGroup;
  commentForms: Record<number, FormGroup> = {};

  postImageFile: File | null = null;
  postImagePreviewUrl: string | null = null;
  postDropActive = false;

  // ── File attachment for new post ──────────────────────────
  postAttachedFile: File | null = null;
  postAttachedFileType: string | null = null;
  postAttachedFileObjectUrl: string | null = null;
  get postAttachedIsVideo(): boolean { return !!this.postAttachedFileType?.startsWith('video/'); }
  get postAttachedIsPdf(): boolean { return this.postAttachedFileType === 'application/pdf'; }

  // ── PDF resize heights ────────────────────────────────────
  threadPdfHeight = 500;
  postPdfHeights: Record<number, number> = {};

  editingThread = false;
  threadEditForm: FormGroup;
  postEditForms: Record<number, FormGroup> = {};
  commentEditForms: Record<number, FormGroup> = {};

  interaction: ThreadInteractionStatus = {
    userVote: null, userReaction: null, wishlisted: false,
    upvoteCount: 0, downvoteCount: 0, voteScore: 0, likeCount: 0, dislikeCount: 0
  };

  interactionError: string | null = null;

  aiSummary: string | null = null;
  aiSummaryLoading = false;
  aiSummaryError: string | null = null;
  aiSummaryPostCount: number | null = null;

  private readonly subs = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private threadApi: ThreadApiService,
    private postApi: PostApiService,
    private commentApi: CommentApiService,
    private interactionApi: ThreadInteractionService,
    private userState: UserStateService,
    public media: MediaUrlService,
    private aiService: AiGenerationService,
    private sanitizer: DomSanitizer,
    public auth: AuthService
  ) {
    this.postForm = this.fb.group({
      body: ['', [Validators.required, Validators.minLength(2)]],
      authorId: [this.auth.currentUserId, [Validators.required]],
    });

    this.threadEditForm = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(200)]],
      body: ['', [Validators.required, Validators.minLength(10)]],
      categoryId: [null],
    });
  }

  onPostImagePicked(file: File | null): void {
    if (this.postImagePreviewUrl) {
      URL.revokeObjectURL(this.postImagePreviewUrl);
    }
    this.postImageFile = file;
    this.postImagePreviewUrl = file ? URL.createObjectURL(file) : null;
  }

  onPostImageInputChange(evt: Event): void {
    const input = evt.target as HTMLInputElement;
    const file = input.files && input.files.length ? input.files[0] : null;
    this.onPostImagePicked(file);
  }

  onPostDropOver(evt: DragEvent): void {
    evt.preventDefault();
    this.postDropActive = true;
  }

  onPostDropLeave(evt: DragEvent): void {
    evt.preventDefault();
    this.postDropActive = false;
  }

  onPostDrop(evt: DragEvent): void {
    evt.preventDefault();
    this.postDropActive = false;
    const file = evt.dataTransfer?.files && evt.dataTransfer.files.length ? evt.dataTransfer.files[0] : null;
    if (file) this.onPostImagePicked(file);
  }

  clearPostImage(): void {
    this.onPostImagePicked(null);
  }

  onPostAttachmentChange(evt: Event): void {
    const input = evt.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (!f) return;
    if (this.postAttachedFileObjectUrl) URL.revokeObjectURL(this.postAttachedFileObjectUrl);
    this.postAttachedFile = f;
    this.postAttachedFileType = f.type;
    this.postAttachedFileObjectUrl = f.type.startsWith('video/') ? URL.createObjectURL(f) : null;
  }

  clearPostAttachment(): void {
    if (this.postAttachedFileObjectUrl) URL.revokeObjectURL(this.postAttachedFileObjectUrl);
    this.postAttachedFile = null;
    this.postAttachedFileType = null;
    this.postAttachedFileObjectUrl = null;
  }

  /** Returns a DomSanitizer-trusted resource URL for iframe/embed src. */
  safeUrl(url: string): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  isVideo(fileType?: string | null): boolean { return !!fileType?.startsWith('video/'); }
  isPdf(fileType?: string | null): boolean   { return fileType === 'application/pdf'; }

  formatFileSize(bytes: number): string {
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getPostPdfHeight(postId: number): number { return this.postPdfHeights[postId] ?? 500; }
  setPostPdfHeight(postId: number, evt: Event): void {
    this.postPdfHeights[postId] = Number((evt.target as HTMLInputElement).value);
  }

  generatePostBody(): void {
    if (!this.thread) return;
    this.postAiError = null;
    this.generatingPost = true;
    this.subs.add(
      this.aiService.generatePostBody(this.thread.title, this.thread.body ?? '').subscribe({
        next: (res) => {
          this.generatingPost = false;
          this.postForm.get('body')?.setValue(res.body);
        },
        error: (err) => {
          this.generatingPost = false;
          this.postAiError = err?.error?.message ?? err?.message ?? 'AI generation failed.';
        },
      })
    );
  }

  lockThread(): void {
    if (!this.threadId) return;
    const uid = this.auth.currentUserId;
    if (!uid) return;
    this.subs.add(this.threadApi.lock(this.threadId, uid).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Lock failed') }));
  }

  unlockThread(): void {
    if (!this.threadId) return;
    const uid = this.auth.currentUserId;
    if (!uid) return;
    this.subs.add(this.threadApi.unlock(this.threadId, uid).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Unlock failed') }));
  }

  closeThread(): void {
    if (!this.threadId) return;
    const uid = this.auth.currentUserId;
    if (!uid) return;
    this.subs.add(this.threadApi.close(this.threadId, uid).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Close failed') }));
  }

  reopenThread(): void {
    if (!this.threadId) return;
    const uid = this.auth.currentUserId;
    if (!uid) return;
    this.subs.add(this.threadApi.reopen(this.threadId, uid).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Reopen failed') }));
  }

  deleteThread(): void {
    if (!this.threadId) return;
    const uid = this.auth.currentUserId;
    if (!uid) return;
    if (!confirm(`Delete thread #${this.threadId}?`)) return;
    this.subs.add(
      this.threadApi.delete(this.threadId, uid).subscribe({
        next: () => this.router.navigate(['/forum']),
        error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Delete thread failed'),
      })
    );
  }

  deletePost(p: PostResponse): void {
    const uid = this.auth.currentUserId;
    if (!uid) return;
    if (!confirm(`Delete post #${p.id}?`)) return;
    this.subs.add(
      this.postApi.delete(p.id, uid).subscribe({
        next: () => this.load(),
        error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Delete post failed'),
      })
    );
  }

  deleteComment(c: CommentResponse): void {
    const uid = this.auth.currentUserId;
    if (!uid) return;
    if (!confirm(`Delete comment #${c.id}?`)) return;
    this.subs.add(
      this.commentApi.delete(c.id, uid).subscribe({
        next: () => this.loadCommentsForPosts(),
        error: (e) => (this.error = e?.error?.message ?? e?.message ?? 'Delete comment failed'),
      })
    );
  }

  get isLocked(): boolean {
    return this.thread?.status === 'LOCKED';
  }

  /** True when the logged-in user is the author of this thread */
  get isOwnThread(): boolean {
    const uid = this.auth.currentUserId;
    return uid !== null && this.thread?.author?.id === uid;
  }

  get canSummarize(): boolean {
    return this.posts.length >= 2;
  }

  summarize(): void {
    if (!this.threadId || this.aiSummaryLoading) return;

    this.aiSummaryLoading = true;
    this.aiSummaryError = null;
    this.aiSummary = null;

    this.subs.add(
      this.aiService.summarizeThread(this.threadId).subscribe({
        next: (response) => {
          this.aiSummary = response.summary;
          this.aiSummaryPostCount = Number.parseInt(response.postCount, 10);
          this.aiSummaryLoading = false;
        },
        error: () => {
          this.aiSummaryError = 'Summary generation failed. Please try again.';
          this.aiSummaryLoading = false;
        },
      })
    );
  }

  ngOnInit(): void {
    // Keep post form authorId in sync with the authenticated user
    this.subs.add(
      this.auth.currentUser$.subscribe(user => {
        if (user?.id) {
          this.postForm.patchValue({ authorId: user.id }, { emitEvent: false });
        }
      })
    );

    this.subs.add(
      this.route.paramMap.subscribe((pm) => {
        const raw = pm.get('threadId');
        const id = raw ? Number(raw) : NaN;
        this.threadId = Number.isFinite(id) ? id : null;
        if (this.threadId === null) {
          this.loading = false;
          this.error = 'Invalid thread id';
          return;
        }
        this.load();
      })
    );
  }

  load(): void {
    if (this.threadId === null) return;

    this.loading = true;
    this.error = null;

    this.subs.add(
      forkJoin({
        thread: this.threadApi.getById(this.threadId),
        posts: this.postApi.listByThread(this.threadId),
      }).subscribe({
        next: ({ thread, posts }) => {
          // Redirect non-owners away from CLOSED threads
          if (thread.status === 'CLOSED' && thread.author?.id !== this.auth.currentUserId) {
            this.router.navigate(['/forum']);
            return;
          }
          this.thread = thread;
          this.posts = posts;
          this.loading = false;
          // Seed interaction counts from the thread response immediately
          this.interaction = {
            userVote: null, userReaction: null, wishlisted: false,
            upvoteCount: thread.upvoteCount ?? 0,
            downvoteCount: thread.downvoteCount ?? 0,
            voteScore: thread.voteScore ?? 0,
            likeCount: thread.likeCount ?? 0,
            dislikeCount: thread.dislikeCount ?? 0
          };
          if (this.thread && !this.editingThread) {
            this.threadEditForm.setValue({
              title: this.thread.title ?? '',
              body: this.thread.body ?? '',
              categoryId: this.thread.category?.id ?? null,
            });
          }
          this.loadCommentsForPosts();
          this.loadInteraction();
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.message ?? 'Failed to load thread';
        },
      })
    );
  }

  loadInteraction(): void {
    if (this.threadId === null) return;
    const userId = this.userState.currentUserId;
    this.subs.add(
      this.interactionApi.getStatus(this.threadId, userId).subscribe({
        next: (status) => { this.interaction = status; },
        error: (err) => { console.warn('Could not load interaction status:', err?.message); }
      })
    );
  }

  onVote(voteType: VoteType): void {
    if (this.threadId === null) return;
    const userId = this.userState.currentUserId;
    if (!userId) { this.interactionError = 'You must be logged in to vote.'; return; }
    this.interactionError = null;
    this.subs.add(
      this.interactionApi.vote(this.threadId, userId, voteType).subscribe({
        next: (status) => { this.interaction = status; },
        error: (err) => {
          console.error('Vote failed:', err);
          this.interactionError = err?.error?.error ?? err?.error?.message ?? err?.message ?? 'Vote failed. Please try again.';
        }
      })
    );
  }

  onReact(reactionType: ReactionType): void {
    if (this.threadId === null) return;
    const userId = this.userState.currentUserId;
    if (!userId) { this.interactionError = 'You must be logged in to react.'; return; }
    this.interactionError = null;
    this.subs.add(
      this.interactionApi.react(this.threadId, userId, reactionType).subscribe({
        next: (status) => { this.interaction = status; },
        error: (err) => {
          console.error('React failed:', err);
          this.interactionError = err?.error?.error ?? err?.error?.message ?? err?.message ?? 'Reaction failed. Please try again.';
        }
      })
    );
  }

  onToggleWishlist(): void {
    if (this.threadId === null) return;
    const userId = this.userState.currentUserId;
    if (!userId) { this.interactionError = 'You must be logged in to save threads.'; return; }
    this.interactionError = null;
    this.subs.add(
      this.interactionApi.toggleWishlist(this.threadId, userId).subscribe({
        next: (status) => { this.interaction = status; },
        error: (err) => {
          console.error('Wishlist toggle failed:', err);
          this.interactionError = err?.error?.error ?? err?.error?.message ?? err?.message ?? 'Save failed. Please try again.';
        }
      })
    );
  }

  loadCommentsForPosts(): void {
    const calls: Record<string, ReturnType<CommentApiService['listByPost']>> = {};
    for (const p of this.posts) {
      calls[String(p.id)] = this.commentApi.listByPost(p.id);
    }

    if (Object.keys(calls).length === 0) {
      this.commentsByPostId = {};
      return;
    }

    this.subs.add(
      forkJoin(calls).subscribe({
        next: (res) => {
          const map: Record<number, CommentResponse[]> = {};
          for (const [postId, comments] of Object.entries(res)) {
            map[Number(postId)] = comments;
          }
          this.commentsByPostId = map;
        },
      })
    );
  }

  ensureCommentForm(postId: number): FormGroup {
    const existing = this.commentForms[postId];
    if (existing) return existing;

    const fg = this.fb.group({
      body: ['', [Validators.required, Validators.minLength(2)]],
      authorId: [this.auth.currentUserId, [Validators.required]],
    });
    this.commentForms[postId] = fg;
    return fg;
  }

  startEditThread(): void {
    if (!this.thread || this.isLocked) return;
    this.editingThread = true;
    this.threadEditForm.setValue({
      title: this.thread.title ?? '',
      body: this.thread.body ?? '',
      categoryId: this.thread.category?.id ?? null,
    });
  }

  cancelEditThread(): void {
    this.editingThread = false;
  }

  saveThread(): void {
    if (this.threadId === null || !this.thread || this.isLocked) return;
    if (this.threadEditForm.invalid) {
      this.threadEditForm.markAllAsTouched();
      return;
    }

    const title = String(this.threadEditForm.value.title ?? '');
    const body = String(this.threadEditForm.value.body ?? '');
    const categoryIdRaw = this.threadEditForm.value.categoryId;
    const categoryId = categoryIdRaw === null || categoryIdRaw === '' ? null : Number(categoryIdRaw);

    const uid = this.auth.currentUserId;
    if (!uid) return;

    this.subs.add(
      this.threadApi.update(this.threadId, uid, { title, body, authorId: uid, categoryId }).subscribe({
        next: () => {
          this.editingThread = false;
          this.load();
        },
        error: (err) => {
          this.error = err?.error?.message ?? err?.message ?? 'Failed to update thread';
        },
      })
    );
  }

  ensurePostEditForm(p: PostResponse): FormGroup {
    const existing = this.postEditForms[p.id];
    if (existing) return existing;

    const fg = this.fb.group({
      body: [p.body ?? '', [Validators.required, Validators.minLength(2)]],
    });
    this.postEditForms[p.id] = fg;
    return fg;
  }

  cancelPostEdit(p: PostResponse): void {
    delete this.postEditForms[p.id];
  }

  savePost(p: PostResponse): void {
    if (this.threadId === null || this.isLocked) return;
    const fg = this.ensurePostEditForm(p);
    if (fg.invalid) {
      fg.markAllAsTouched();
      return;
    }

    const body = String(fg.value.body ?? '');
    this.subs.add(
      this.postApi
        .update(p.id, { body, threadId: this.threadId, authorId: p.author?.id ?? p.authorId ?? this.auth.currentUserId ?? 0 })
        .subscribe({
          next: () => {
            delete this.postEditForms[p.id];
            this.load();
          },
          error: (err) => {
            this.error = err?.message ?? 'Failed to update post';
          },
        })
    );
  }

  ensureCommentEditForm(c: CommentResponse): FormGroup {
    const existing = this.commentEditForms[c.id];
    if (existing) return existing;

    const fg = this.fb.group({
      body: [c.body ?? '', [Validators.required, Validators.minLength(2)]],
    });
    this.commentEditForms[c.id] = fg;
    return fg;
  }

  cancelCommentEdit(c: CommentResponse): void {
    delete this.commentEditForms[c.id];
  }

  saveComment(c: CommentResponse): void {
    if (this.isLocked) return;
    const fg = this.ensureCommentEditForm(c);
    if (fg.invalid) {
      fg.markAllAsTouched();
      return;
    }

    const body = String(fg.value.body ?? '');
    this.subs.add(
      this.commentApi
        .update(c.id, { body, postId: c.postId, authorId: c.author?.id ?? c.authorId ?? this.auth.currentUserId ?? 0 })
        .subscribe({
          next: () => {
            delete this.commentEditForms[c.id];
            this.loadCommentsForPosts();
          },
          error: (err) => {
            this.error = err?.message ?? 'Failed to update comment';
          },
        })
    );
  }

  submitPost(): void {
    if (this.threadId === null) return;
    if (this.isLocked) {
      this.error = 'Thread is locked';
      return;
    }

    this.postSubmitAttempted = true;

    if (this.postForm.invalid) {
      this.postForm.markAllAsTouched();
      return;
    }

    const body = String(this.postForm.value.body ?? '');
    const authorId = this.auth.currentUserId ?? Number(this.postForm.value.authorId);
    if (!Number.isFinite(authorId) || authorId <= 0) {
      this.postForm.get('authorId')?.setErrors({ invalid: true });
      return;
    }

    if (this.submittingPost) return;
    this.submittingPost = true;

    this.subs.add(
      this.postApi.create({ body, threadId: this.threadId, authorId }, this.postImageFile, this.postAttachedFile).subscribe({
        next: () => {
          this.postForm.reset();
          this.clearPostImage();
          this.clearPostAttachment();
          this.postSubmitAttempted = false;
          this.submittingPost = false;
          this.userState.refresh();
          this.load();
        },
        error: (err) => {
          this.submittingPost = false;
          this.error = err?.error?.error ?? err?.error?.message ?? err?.message ?? 'Failed to create post';
        },
      })
    );
  }

  submitComment(postId: number): void {
    const form = this.ensureCommentForm(postId);
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }

    const body = String(form.value.body ?? '');
    const authorId = this.auth.currentUserId ?? Number(form.value.authorId);

    this.subs.add(
      this.commentApi.create({ body, postId, authorId, threadId: this.threadId ?? undefined }).subscribe({
        next: () => {
          form.reset();
          this.userState.refresh();
          this.loadCommentsForPosts();
        },
        error: (err) => {
          this.error = err?.error?.error ?? err?.error?.message ?? err?.message ?? 'Failed to create comment';
        },
      })
    );
  }

  ngOnDestroy(): void {
    if (this.postImagePreviewUrl) URL.revokeObjectURL(this.postImagePreviewUrl);
    if (this.postAttachedFileObjectUrl) URL.revokeObjectURL(this.postAttachedFileObjectUrl);
    this.subs.unsubscribe();
  }
}
