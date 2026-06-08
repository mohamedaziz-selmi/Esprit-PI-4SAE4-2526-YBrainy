import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';

import { ThreadResponse } from '../../frontoffice/models/forum.models';
import { ThreadApiService } from '../../frontoffice/services/thread-api.service';
import { BackofficeNavComponent } from '../backoffice-nav/backoffice-nav.component';

@Component({
  selector: 'app-backoffice-forum-threads',
  standalone: true,
  imports: [CommonModule, RouterModule, BackofficeNavComponent],
  templateUrl: './backoffice-forum-threads.component.html',
  styleUrl: './backoffice-forum-threads.component.css',
  host: { style: 'display:block' },
})
export class BackofficeForumThreadsComponent implements OnInit, OnDestroy {
  threads: ThreadResponse[] = [];
  loading = true;
  error: string | null = null;

  private readonly subs = new Subscription();

  constructor(private threadApi: ThreadApiService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;

    this.subs.add(
      this.threadApi.list().subscribe({
        next: (threads) => {
          this.threads = threads;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.message ?? 'Failed to load threads';
        },
      })
    );
  }

  openThread(threadId: number): void {
    this.router.navigate(['/dashboard/forum/threads', threadId]);
  }

  private getAuthorId(t: ThreadResponse): number | null {
    return t.author?.id ?? t.authorId ?? null;
  }

  lock(t: ThreadResponse): void {
    const authorId = this.getAuthorId(t);
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.lock(t.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Lock failed') }));
  }

  unlock(t: ThreadResponse): void {
    const authorId = this.getAuthorId(t);
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.unlock(t.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Unlock failed') }));
  }

  close(t: ThreadResponse): void {
    const authorId = this.getAuthorId(t);
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.close(t.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Close failed') }));
  }

  reopen(t: ThreadResponse): void {
    const authorId = this.getAuthorId(t);
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    this.subs.add(this.threadApi.reopen(t.id, authorId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.message ?? 'Reopen failed') }));
  }

  deleteThread(t: ThreadResponse): void {
    const authorId = this.getAuthorId(t);
    if (authorId === null) { this.error = 'Thread author is missing'; return; }
    if (!confirm(`Delete thread #${t.id}?`)) return;
    this.subs.add(
      this.threadApi.delete(t.id, authorId).subscribe({
        next: () => this.load(),
        error: (e) => (this.error = e?.message ?? 'Delete failed'),
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }
}
