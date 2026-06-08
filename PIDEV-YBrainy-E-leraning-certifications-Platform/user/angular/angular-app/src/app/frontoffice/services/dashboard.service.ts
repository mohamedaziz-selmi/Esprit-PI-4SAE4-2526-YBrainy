import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserDashboard, XpDataPoint, DayActivity } from '../models/forum.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly base = '/api/dashboard';

  constructor(private http: HttpClient) {}

  getDashboard(userId: number): Observable<UserDashboard> {
    return this.http.get<UserDashboard>(`${this.base}/${userId}`);
  }

  getXpTimeline(userId: number): Observable<XpDataPoint[]> {
    return this.http.get<XpDataPoint[]>(`${this.base}/${userId}/xp-timeline`);
  }

  getActivity(userId: number): Observable<DayActivity[]> {
    return this.http.get<DayActivity[]>(`${this.base}/${userId}/activity`);
  }
}
