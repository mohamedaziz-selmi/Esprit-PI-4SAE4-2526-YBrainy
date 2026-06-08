import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * API client that matches your Spring controller:
 * - GET    /api/courses
 * - GET    /api/courses/category/{category}
 * - GET    /api/courses/{id}
 * - POST   /api/courses           (multipart/form-data)
 * - PUT    /api/courses/{id}      (multipart/form-data)
 * - DELETE /api/courses/{id}
 *
 * Note: Not wired into the UI yet (UI currently uses CourseStoreService for static demo),
 * but this is ready for when you connect the backend.
 */

export interface ApiCourse {
  id: number;
  title: string;
  category?: string;
  description?: string;
  thumbnailVideoPath?: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class CourseApiService {
  private readonly base = '/api/courses';

  constructor(private http: HttpClient) {}

  list(): Observable<ApiCourse[]> {
    return this.http.get<ApiCourse[]>(this.base);
  }

  listByCategory(category: string): Observable<ApiCourse[]> {
    return this.http.get<ApiCourse[]>(`${this.base}/category/${encodeURIComponent(category)}`);
  }

  getById(id: number): Observable<ApiCourse> {
    return this.http.get<ApiCourse>(`${this.base}/${id}`);
  }

  create(input: {
    title: string;
    category?: string;
    description?: string;
    thumbnailVideo?: File | null;
  }): Observable<ApiCourse> {
    const fd = new FormData();
    fd.append('title', input.title);
    if (input.category) fd.append('category', input.category);
    if (input.description) fd.append('description', input.description);
    if (input.thumbnailVideo) fd.append('thumbnailVideo', input.thumbnailVideo);
    return this.http.post<ApiCourse>(this.base, fd);
  }

  update(
    id: number,
    input: { title: string; category?: string; description?: string; thumbnailVideo?: File | null }
  ): Observable<ApiCourse> {
    const fd = new FormData();
    fd.append('title', input.title);
    if (input.category) fd.append('category', input.category);
    if (input.description) fd.append('description', input.description);
    if (input.thumbnailVideo) fd.append('thumbnailVideo', input.thumbnailVideo);
    return this.http.put<ApiCourse>(`${this.base}/${id}`, fd);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}


