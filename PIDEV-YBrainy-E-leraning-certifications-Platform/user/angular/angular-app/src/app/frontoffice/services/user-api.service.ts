import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { UserProfileResponse, XpEventResponse } from '../models/forum.models';

@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly base = '/api/forum-users';

  constructor(private http: HttpClient) {}

  getProfile(userId: number): Observable<UserProfileResponse> {
    return this.http.get<UserProfileResponse>(`${this.base}/${userId}/profile`);
  }

  getRecentXp(userId: number): Observable<XpEventResponse[]> {
    return this.http.get<XpEventResponse[]>(`${this.base}/${userId}/xp-recent`);
  }

  getXpHistory(userId: number): Observable<XpEventResponse[]> {
    return this.http.get<XpEventResponse[]>(`${this.base}/${userId}/xp-history`);
  }
}
