import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Pack,
  CreatePack,
  UpdatePack,
  PackLevel,
  PackStatus,
  PageResponse,
  GeneratePackContentRequest,
  GeneratePackContentResponse
} from '../models/pack.model';

@Injectable({ providedIn: 'root' })
export class PackService {
  private readonly apiBase = environment.apiUrl || 'http://localhost:8091/api';
  private readonly adminBase = `${this.apiBase}/admin/packs`;
  private readonly publicBase = `${this.apiBase}/packs`;

  constructor(private http: HttpClient) { }

  /* Admin */
  getAll(params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
    categoryId?: number;
    level?: PackLevel;
    status?: PackStatus;
  } = {}): Observable<PageResponse<Pack>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    if (params.sortBy) httpParams = httpParams.set('sortBy', params.sortBy);
    if (params.sortDir) httpParams = httpParams.set('sortDir', params.sortDir);
    if (params.categoryId) httpParams = httpParams.set('categoryId', params.categoryId);
    if (params.level) httpParams = httpParams.set('level', params.level);
    if (params.status) httpParams = httpParams.set('status', params.status);
    return this.http.get<PageResponse<Pack>>(this.adminBase, { params: httpParams });
  }

  getById(id: number): Observable<Pack> {
    return this.http.get<Pack>(`${this.adminBase}/${id}`);
  }

  create(dto: CreatePack, file?: File): Observable<Pack> {
    const formData = new FormData();
    formData.append('pack', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
    if (file) {
      formData.append('file', file);
    }
    return this.http.post<Pack>(this.adminBase, formData);
  }

  update(id: number, dto: UpdatePack, file?: File): Observable<Pack> {
    const formData = new FormData();
    formData.append('pack', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
    if (file) {
      formData.append('file', file);
    }
    return this.http.put<Pack>(`${this.adminBase}/${id}`, formData);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminBase}/${id}`);
  }

  changeStatus(id: number, status: PackStatus): Observable<Pack> {
    return this.http.patch<Pack>(`${this.adminBase}/${id}/status`, { status });
  }

  generateContent(payload: GeneratePackContentRequest): Observable<GeneratePackContentResponse> {
    return this.http.post<GeneratePackContentResponse>(`${this.adminBase}/content/generate`, payload);
  }

  /* Public (Frontoffice) */
  getActivePacks(): Observable<Pack[]> {
    return this.http.get<Pack[]>(`${this.publicBase}/active`);
  }

  getActivePacksByCategory(categoryId: number): Observable<Pack[]> {
    return this.http.get<Pack[]>(`${this.publicBase}/category/${categoryId}`);
  }

  getActivePackById(id: number): Observable<Pack> {
    return this.http.get<Pack>(`${this.publicBase}/${id}`);
  }

  getPackTextToSpeech(id: number): Observable<Blob> {
    return this.http.post(`${this.publicBase}/${id}/text-to-speech`, {}, { responseType: 'blob' });
  }
}
