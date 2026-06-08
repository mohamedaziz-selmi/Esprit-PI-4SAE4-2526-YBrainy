import { Injectable } from '@angular/core';

/**
 * In-memory store for files attached during AI-assisted draft creation.
 * Files cannot be persisted to localStorage, so they live here until
 * the user opens the draft in CreateThreadComponent.
 */
@Injectable({ providedIn: 'root' })
export class AiDraftFilesService {
  private readonly store = new Map<string, File[]>();

  set(draftId: string, files: File[]): void {
    if (files.length) {
      this.store.set(draftId, [...files]);
    }
  }

  get(draftId: string): File[] {
    return this.store.get(draftId) ?? [];
  }

  clear(draftId: string): void {
    this.store.delete(draftId);
  }
}
