import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';

import { CommentResponse, PostResponse, ThreadResponse } from '../../frontoffice/models/forum.models';
import { CommentApiService } from '../../frontoffice/services/comment-api.service';
import { PostApiService } from '../../frontoffice/services/post-api.service';
import { ThreadApiService } from '../../frontoffice/services/thread-api.service';
import { BackofficeNavComponent } from '../backoffice-nav/backoffice-nav.component';

@Component({
  selector: 'app-backoffice-forum-thread-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, BackofficeNavComponent],
  templateUrl: './backoffice-forum-thread-detail.component.html',
  styleUrl: './backoffice-forum-thread-detail.component.css',
  host: { style: 'display:block' },
})
export class BackofficeForumThreadDetailComponent implements OnInit, OnDestroy {
  threadId: number | null = null;
  thread: ThreadResponse | null = null;
  posts: PostResponse[] = [];
  commentsByPostId: Record<number, CommentResponse[]> = {};

  loading = true;
  error: string | null = null;

  private readonly subs = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private threadApi: ThreadApiService,
    private postApi: PostApiService,
    private commentApi: CommentApiService
  ) {}

  ngOnInit(): void {
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

  back(): void {
    this.router.navigate(['/dashboard/forum/threads']);
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
          this.thread = thread;
          this.posts = posts;
          this.loading = false;
          this.loadComments();
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.message ?? 'Failed to load thread';
        },
      })
    );
  }

  private loadComments(): void {
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

  private getThreadAuthorId(): number | null {
    return this.thread?.author?.id ?? this.thread?.authorId ?? null;
  }

  private getPostAuthorId(p: PostResponse): number | null {
    return p.author?.id ?? p.authorId ?? null;
  }

  private getCommentAuthorId(c: CommentResponse): number | null {
    return c.author?.id ?? c.authorId ?? null;
  }

  lock(): void {
    if (!this.thread) return;
    const authorId = this.getThreadAuthorId();
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.lock(this.thread.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Lock failed') }));
  }

  unlock(): void {
    if (!this.thread) return;
    const authorId = this.getThreadAuthorId();
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.unlock(this.thread.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Unlock failed') }));
  }

  close(): void {
    if (!this.thread) return;
    const authorId = this.getThreadAuthorId();
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.close(this.thread.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Close failed') }));
  }

  reopen(): void {
    if (!this.thread) return;
    const authorId = this.getThreadAuthorId();
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.reopen(this.thread.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Reopen failed') }));
  }

  deleteThread(): void {
    if (!this.thread) return;
    const authorId = this.getThreadAuthorId();
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    if (!confirm(`Delete thread #${this.thread.id}?`)) return;

    this.subs.add(
      this.threadApi.delete(this.thread.id, authorId).subscribe({
        next: () => this.back(),
        error: (e) => (this.error = e?.message ?? 'Delete failed'),
      })
    );
  }

  deletePost(p: PostResponse): void {
    const authorId = this.getPostAuthorId(p);
    if (authorId === null) { this.error = 'Post author is missing'; return; }
    if (!confirm(`Delete post #${p.id}?`)) return;
    this.subs.add(
      this.postApi.delete(p.id, authorId).subscribe({
        next: () => this.load(),
        error: (e) => (this.error = e?.message ?? 'Delete post failed'),
      })
    );
  }

  deleteComment(c: CommentResponse): void {
    const authorId = this.getCommentAuthorId(c);
    if (authorId === null) { this.error = 'Comment author is missing'; return; }
    if (!confirm(`Delete comment #${c.id}?`)) return;
    this.subs.add(
      this.commentApi.delete(c.id, authorId).subscribe({
        next: () => this.load(),
        error: (e) => (this.error = e?.message ?? 'Delete comment failed'),
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }
}
