import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';
import { Partnership } from '../models/partnership.models';

interface BackendPage<T> {
  content: T[];
}

interface BackendPartnershipResponse {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  website: string | null;
  industry?: string | null;
  description: string | null;
  logoDataUrl?: string | null;
  active: boolean;
  createdAt: string;
}

interface BackendPartnershipRequest {
  name: string;
  email: string;
  phone: string | null;
  website: string | null;
  description: string | null;
  active: boolean;
}

const API_BASE_URL = '/api/partnerships';
const VISUAL_CACHE_KEY = 'bo.partnership.visual.v1';

type PartnershipVisual = Pick<Partnership, 'industry' | 'logoDataUrl'>;

@Injectable({ providedIn: 'root' })
export class PartnershipStoreService {
  private readonly _items$ = new BehaviorSubject<Partnership[]>([]);
  readonly items$ = this._items$.asObservable();
  private visualCache = this.loadVisualCache();

  constructor(private http: HttpClient) {
    this.reload().subscribe({ error: () => this.setItems([]) });
  }

  get snapshot(): Partnership[] {
    return this._items$.value;
  }

  getById(id: string): Partnership | undefined {
    return this.snapshot.find((p) => p.id === id);
  }

  reload(): Observable<Partnership[]> {
    const currentById = new Map(this.snapshot.map((p) => [p.id, p]));
    return this.http
      .get<BackendPage<BackendPartnershipResponse>>(`${API_BASE_URL}?page=0&size=200&sortBy=createdAt&direction=DESC`)
      .pipe(
        map((res) =>
          res.content.map((item) =>
            this.fromBackend(item, this.visualCache[item.id] ?? currentById.get(item.id))
          )
        ),
        tap((items) => this.setItems(items))
      );
  }

  add(input: Omit<Partnership, 'id' | 'createdAt'>): Observable<Partnership> {
    return this.http.post<BackendPartnershipResponse>(API_BASE_URL, this.toBackend(input)).pipe(
      map((created) => this.fromBackend(created, input)),
      tap((created) => this.setItems([created, ...this.snapshot]))
    );
  }

  update(id: string, patch: Partial<Omit<Partnership, 'id' | 'createdAt'>>): Observable<Partnership> {
    const current = this.getById(id);
    if (!current) {
      throw new Error(`Partnership not found in store: ${id}`);
    }

    const merged: Omit<Partnership, 'id' | 'createdAt'> = { ...current, ...patch };
    return this.http.put<BackendPartnershipResponse>(`${API_BASE_URL}/${id}`, this.toBackend(merged)).pipe(
      map((updated) => this.fromBackend(updated, merged)),
      tap((updated) => this.setItems(this.snapshot.map((p) => (p.id === id ? updated : p))))
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/${id}`).pipe(
      tap(() => this.setItems(this.snapshot.filter((p) => p.id !== id)))
    );
  }

  resetToSeed(): void {
    this.reload().subscribe();
  }

  private fromBackend(
    item: BackendPartnershipResponse,
    fallback?: PartnershipVisual
  ): Partnership {
    return {
      id: item.id,
      name: item.name,
      email: item.email,
      phone: item.phone ?? undefined,
      website: item.website ?? undefined,
      industry: item.industry ?? fallback?.industry,
      description: item.description ?? undefined,
      logoDataUrl: item.logoDataUrl ?? fallback?.logoDataUrl,
      createdAt: item.createdAt,
      isActive: item.active,
    };
  }

  private toBackend(input: Omit<Partnership, 'id' | 'createdAt'>): BackendPartnershipRequest {
    return {
      name: input.name.trim(),
      email: (input.email ?? '').trim(),
      phone: input.phone?.trim() || null,
      website: input.website?.trim() || null,
      description: input.description?.trim() || null,
      active: input.isActive,
    };
  }

  private setItems(items: Partnership[]): void {
    this._items$.next(items);
    this.persistVisualCache(items);
  }

  private loadVisualCache(): Record<string, PartnershipVisual> {
    try {
      if (typeof localStorage === 'undefined') return {};
      const raw = localStorage.getItem(VISUAL_CACHE_KEY);
      if (!raw) return {};
      const parsed = JSON.parse(raw) as Record<string, PartnershipVisual> | null;
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  }

  private persistVisualCache(items: Partnership[]): void {
    const nextCache: Record<string, PartnershipVisual> = {};
    for (const item of items) {
      if (!item.industry && !item.logoDataUrl) continue;
      nextCache[item.id] = {
        industry: item.industry,
        logoDataUrl: item.logoDataUrl,
      };
    }
    this.visualCache = nextCache;

    try {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(VISUAL_CACHE_KEY, JSON.stringify(this.visualCache));
    } catch {
      // ignore storage quota/privacy issues
    }
  }
}
