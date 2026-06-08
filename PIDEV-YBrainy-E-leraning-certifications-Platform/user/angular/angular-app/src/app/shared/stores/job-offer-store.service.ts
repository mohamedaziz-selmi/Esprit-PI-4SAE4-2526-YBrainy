import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';
import { JobContractType, JobOffer } from '../models/job-offer.models';

type BackendContractType = 'CDI' | 'CDD' | 'STAGE' | 'FREELANCE' | 'ALTERNANCE';
type BackendOfferStatus = 'DRAFT' | 'OPEN' | 'CLOSED';

interface BackendPage<T> {
  content: T[];
}

interface BackendJobOfferResponse {
  id: string;
  title: string;
  description: string;
  location: string | null;
  imageDataUrl: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  contractType: BackendContractType;
  status: BackendOfferStatus;
  deadline: string | null;
  partnershipId: string;
  createdAt: string;
}

interface BackendJobOfferRequest {
  title: string;
  description: string;
  location: string | null;
  imageDataUrl: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  contractType: BackendContractType;
  status: BackendOfferStatus;
  deadline: string | null;
  partnershipId: string;
}

const API_BASE_URL = '/api/offers';

@Injectable({ providedIn: 'root' })
export class JobOfferStoreService {
  private readonly _items$ = new BehaviorSubject<JobOffer[]>([]);
  readonly items$ = this._items$.asObservable();

  constructor(private http: HttpClient) {
    this.reload().subscribe({ error: () => this._items$.next([]) });
  }

  get snapshot(): JobOffer[] {
    return this._items$.value;
  }

  getById(id: string): JobOffer | undefined {
    return this.snapshot.find((o) => o.id === id);
  }

  reload(): Observable<JobOffer[]> {
    return this.http
      .get<BackendPage<BackendJobOfferResponse>>(`${API_BASE_URL}?page=0&size=200&sortBy=createdAt&direction=DESC`)
      .pipe(
        map((res) => res.content.map((item) => this.fromBackend(item))),
        tap((items) => this._items$.next(items))
      );
  }

  add(input: Omit<JobOffer, 'id' | 'createdAt'>): Observable<JobOffer> {
    return this.http.post<BackendJobOfferResponse>(API_BASE_URL, this.toBackend(input)).pipe(
      map((created) => this.fromBackend(created)),
      tap((created) => this._items$.next([created, ...this.snapshot]))
    );
  }

  update(id: string, patch: Partial<Omit<JobOffer, 'id' | 'createdAt'>>): Observable<JobOffer> {
    const current = this.getById(id);
    if (!current) {
      throw new Error(`Job offer not found in store: ${id}`);
    }

    const merged: Omit<JobOffer, 'id' | 'createdAt'> = { ...current, ...patch };
    return this.http.put<BackendJobOfferResponse>(`${API_BASE_URL}/${id}`, this.toBackend(merged)).pipe(
      map((updated) => this.fromBackend(updated)),
      tap((updated) => this._items$.next(this.snapshot.map((o) => (o.id === id ? updated : o))))
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/${id}`).pipe(
      tap(() => this._items$.next(this.snapshot.filter((o) => o.id !== id)))
    );
  }

  resetToSeed(): void {
    this.reload().subscribe();
  }

  private fromBackend(item: BackendJobOfferResponse): JobOffer {
    return {
      id: item.id,
      partnershipId: item.partnershipId,
      title: item.title,
      location: item.location ?? undefined,
      imageDataUrl: item.imageDataUrl ?? undefined,
      contractType: this.contractTypeFromBackend(item.contractType),
      description: item.description,
      salaryRange: this.salaryRangeFromBackend(item.salaryMin, item.salaryMax),
      deadline: item.deadline ?? undefined,
      createdAt: item.createdAt,
      isActive: item.status === 'OPEN',
    };
  }

  private toBackend(input: Omit<JobOffer, 'id' | 'createdAt'>): BackendJobOfferRequest {
    const salary = this.parseSalaryRange(input.salaryRange);
    return {
      title: input.title.trim(),
      description: (input.description ?? '').trim(),
      location: input.location?.trim() || null,
      imageDataUrl: input.imageDataUrl?.trim() || null,
      salaryMin: salary.salaryMin,
      salaryMax: salary.salaryMax,
      contractType: this.contractTypeToBackend(input.contractType),
      status: input.isActive ? 'OPEN' : 'CLOSED',
      deadline: this.toLocalDate(input.deadline),
      partnershipId: input.partnershipId.trim(),
    };
  }

  private contractTypeFromBackend(value: BackendContractType): JobContractType {
    switch (value) {
      case 'STAGE':
        return 'Stage';
      case 'ALTERNANCE':
        return 'Alternance';
      case 'FREELANCE':
        return 'Freelance';
      case 'CDD':
        return 'CDD';
      case 'CDI':
      default:
        return 'CDI';
    }
  }

  private contractTypeToBackend(value: JobContractType): BackendContractType {
    switch (value) {
      case 'Stage':
        return 'STAGE';
      case 'Alternance':
        return 'ALTERNANCE';
      case 'Freelance':
        return 'FREELANCE';
      case 'CDD':
        return 'CDD';
      case 'CDI':
      case 'Autre':
      default:
        return 'CDI';
    }
  }

  private salaryRangeFromBackend(min: number | null, max: number | null): string | undefined {
    if (min == null && max == null) return undefined;
    if (min != null && max != null) return `${min}-${max}`;
    return String(min ?? max);
  }

  private parseSalaryRange(value?: string): { salaryMin: number | null; salaryMax: number | null } {
    if (!value?.trim()) return { salaryMin: null, salaryMax: null };
    const normalized = value.replace(',', '.');
    const numbers = normalized.match(/\d+(\.\d+)?/g) ?? [];
    if (numbers.length === 0) return { salaryMin: null, salaryMax: null };
    if (numbers.length === 1) {
      const single = Number(numbers[0]);
      return Number.isFinite(single) ? { salaryMin: single, salaryMax: null } : { salaryMin: null, salaryMax: null };
    }
    const min = Number(numbers[0]);
    const max = Number(numbers[1]);
    if (!Number.isFinite(min) || !Number.isFinite(max)) return { salaryMin: null, salaryMax: null };
    return min <= max ? { salaryMin: min, salaryMax: max } : { salaryMin: max, salaryMax: min };
  }

  private toLocalDate(value?: string): string | null {
    if (!value?.trim()) return null;
    return value.slice(0, 10);
  }
}
