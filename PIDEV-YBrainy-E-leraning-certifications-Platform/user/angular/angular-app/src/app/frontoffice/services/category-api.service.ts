import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { CategoryResponse } from '../models/forum.models';

export interface CategoryRequest {
  name: string;
  description?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CategoryApiService {
  private readonly base = '/api/categories';

  constructor(private http: HttpClient) {}

  list(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>(this.base);
  }

  getById(id: number): Observable<CategoryResponse> {
    return this.http.get<CategoryResponse>(`${this.base}/${id}`);
  }

  create(input: CategoryRequest): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>(this.base, input);
  }

  update(id: number, input: CategoryRequest): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`${this.base}/${id}`, input);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
