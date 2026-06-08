import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { NotificationResponse } from '../models/forum.models';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly base = '/api/notifications';

  constructor(private http: HttpClient) {}

  getAll(userId: number): Observable<NotificationResponse[]> {
    return this.http.get<NotificationResponse[]>(this.base, { params: { userId: String(userId) } });
  }

  getUnreadCount(userId: number): Observable<number> {
    return this.http.get<number>(`${this.base}/count`, { params: { userId: String(userId) } });
  }

  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/read`, {});
  }

  markAllAsRead(userId: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/read-all`, {}, { params: { userId: String(userId) } });
  }
}
