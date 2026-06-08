import { Injectable } from '@angular/core';

export interface DraftAttachmentMeta {
  name: string;
  type: string;
  size: number;
}

export interface ThreadDraft {
  id: string;
  title: string;
  body: string;
  categoryId: number | null;
  savedAt: string;
  attachments?: DraftAttachmentMeta[];
}

const STORAGE_KEY = 'forum_drafts';

@Injectable({ providedIn: 'root' })
export class DraftService {
  getAll(): ThreadDraft[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  getById(id: string): ThreadDraft | null {
    return this.getAll().find((d) => d.id === id) ?? null;
  }

  save(draft: Omit<ThreadDraft, 'id' | 'savedAt'>, id?: string): ThreadDraft {
    const drafts = this.getAll();
    const saved: ThreadDraft = {
      ...draft,
      id: id ?? this.uuid(),
      savedAt: new Date().toISOString(),
    };
    const idx = drafts.findIndex((d) => d.id === saved.id);
    if (idx >= 0) {
      drafts[idx] = saved;
    } else {
      drafts.unshift(saved);
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(drafts));
    return saved;
  }

  delete(id: string): void {
    const drafts = this.getAll().filter((d) => d.id !== id);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(drafts));
  }

  private uuid(): string {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }
}
