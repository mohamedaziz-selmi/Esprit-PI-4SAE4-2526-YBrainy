import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

import { DraftService, ThreadDraft } from '../../../services/draft.service';

@Component({
  selector: 'app-drafts',
  standalone: false,
  templateUrl: './drafts.component.html',
  styleUrl: './drafts.component.css',
  host: { style: 'display:block' },
})
export class DraftsComponent implements OnInit {
  drafts: ThreadDraft[] = [];

  constructor(private draftService: DraftService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.drafts = this.draftService.getAll();
  }

  open(draft: ThreadDraft): void {
    this.router.navigate(['/forum/create'], { queryParams: { draftId: draft.id } });
  }

  delete(id: string): void {
    this.draftService.delete(id);
    this.load();
  }

  formatDate(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
