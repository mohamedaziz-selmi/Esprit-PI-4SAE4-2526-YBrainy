import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ThreadRequest, ThreadResponse } from '../models/forum.models';

@Injectable({ providedIn: 'root' })
export class ThreadApiService {
  private readonly base = '/api/threads';

  constructor(private http: HttpClient) {}

  list(): Observable<ThreadResponse[]> {
    return this.http.get<ThreadResponse[]>(this.base);
  }

  getById(id: number): Observable<ThreadResponse> {
    return this.http.get<ThreadResponse>(`${this.base}/${id}`);
  }

  listByCategory(categoryId: number): Observable<ThreadResponse[]> {
    return this.http.get<ThreadResponse[]>(`${this.base}/category/${categoryId}`);
  }

  listByAuthor(authorId: number): Observable<ThreadResponse[]> {
    return this.http.get<ThreadResponse[]>(`${this.base}/author/${authorId}`);
  }

  create(input: ThreadRequest, image?: File | null, file?: File | null): Observable<ThreadResponse> {
    const formData = new FormData();
    formData.append('title', input.title ?? '');
    formData.append('body', input.body ?? '');
    formData.append('authorId', String(input.authorId ?? ''));

    if (input.categoryId != null) {
      formData.append('categoryId', String(input.categoryId));
    }
    if (image) {
      formData.append('image', image);
    }
    if (file) {
      formData.append('file', file);
    }

    return this.http.post<ThreadResponse>(this.base, formData);
  }

  update(id: number, userId: number, input: Partial<ThreadRequest>): Observable<ThreadResponse> {
    return this.http.put<ThreadResponse>(
      `${this.base}/${id}`,
      input,
      { params: { userId: String(userId) } }
    );
  }

  delete(id: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { params: { userId: String(userId) } });
  }

  lock(id: number, userId: number): Observable<ThreadResponse> {
    return this.http.patch<ThreadResponse>(`${this.base}/${id}/lock`, {}, { params: { userId: String(userId) } });
  }

  unlock(id: number, userId: number): Observable<ThreadResponse> {
    return this.http.patch<ThreadResponse>(`${this.base}/${id}/unlock`, {}, { params: { userId: String(userId) } });
  }

  close(id: number, userId: number): Observable<ThreadResponse> {
    return this.http.patch<ThreadResponse>(`${this.base}/${id}/close`, {}, { params: { userId: String(userId) } });
  }

  reopen(id: number, userId: number): Observable<ThreadResponse> {
    return this.http.patch<ThreadResponse>(`${this.base}/${id}/reopen`, {}, { params: { userId: String(userId) } });
  }
}
