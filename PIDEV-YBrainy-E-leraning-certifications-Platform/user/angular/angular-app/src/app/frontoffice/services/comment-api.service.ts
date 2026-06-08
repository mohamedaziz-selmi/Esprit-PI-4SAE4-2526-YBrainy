import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommentRequest, CommentResponse } from '../models/forum.models';

@Injectable({ providedIn: 'root' })
export class CommentApiService {
  private readonly base = '/api/comments';

  constructor(private http: HttpClient) {}

  list(): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(this.base);
  }

  getById(id: number): Observable<CommentResponse> {
    return this.http.get<CommentResponse>(`${this.base}/${id}`);
  }

  listByPost(postId: number): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${this.base}/post/${postId}`);
  }

  listByAuthor(authorId: number): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${this.base}/author/${authorId}`);
  }

  countByPost(postId: number): Observable<number> {
    return this.http.get<number>(`${this.base}/post/${postId}/count`);
  }

  create(input: CommentRequest): Observable<CommentResponse> {
    return this.http.post<CommentResponse>(this.base, input);
  }

  update(id: number, input: CommentRequest): Observable<CommentResponse> {
    return this.http.put<CommentResponse>(`${this.base}/${id}`, input);
  }

  delete(id: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { params: { userId: String(userId) } });
  }
}
