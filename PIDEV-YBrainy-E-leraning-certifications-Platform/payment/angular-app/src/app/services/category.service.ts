import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PackCategory, CreatePackCategory, UpdatePackCategory } from '../models/pack-category.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly adminBase = `${environment.apiUrl}/admin/categories`;
  private readonly publicBase = `${environment.apiUrl}/categories`;

  constructor(private http: HttpClient) {}

  /* ─── Admin ─── */
  getAll(): Observable<PackCategory[]> {
    return this.http.get<PackCategory[]>(this.adminBase);
  }

  getById(id: number): Observable<PackCategory> {
    return this.http.get<PackCategory>(`${this.adminBase}/${id}`);
  }

  create(dto: CreatePackCategory): Observable<PackCategory> {
    return this.http.post<PackCategory>(this.adminBase, dto);
  }

  update(id: number, dto: UpdatePackCategory): Observable<PackCategory> {
    return this.http.put<PackCategory>(`${this.adminBase}/${id}`, dto);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminBase}/${id}`);
  }

  toggleStatus(id: number): Observable<PackCategory> {
    return this.http.patch<PackCategory>(`${this.adminBase}/${id}/status`, {});
  }

  /* ─── Public (Frontoffice) ─── */
  getActiveCategories(): Observable<PackCategory[]> {
    return this.http.get<PackCategory[]>(`${this.publicBase}/active`);
  }
}

