import { Injectable } from '@angular/core';
import { BehaviorSubject, forkJoin, Observable } from 'rxjs';

import { UserProfileResponse, XpEventResponse } from '../models/forum.models';
import { UserApiService } from './user-api.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class UserStateService {
  private readonly profileSubject = new BehaviorSubject<UserProfileResponse | null>(null);
  profile$ = this.profileSubject.asObservable();

  private readonly recentXpSubject = new BehaviorSubject<XpEventResponse[]>([]);
  recentXp$ = this.recentXpSubject.asObservable();

  private loading = false;

  constructor(private userApi: UserApiService, private auth: AuthService) {}

  /** Returns the currently logged-in user's ID (or 0 if not logged in). */
  get currentUserId(): number {
    return this.auth.currentUserId ?? 0;
  }

  get profileSnapshot(): UserProfileResponse | null {
    return this.profileSubject.value;
  }

  refresh(): void {
    const id = this.currentUserId;
    if (!id || this.loading) return;
    this.loading = true;

    forkJoin({
      profile: this.userApi.getProfile(id),
      recent: this.userApi.getRecentXp(id),
    }).subscribe({
      next: ({ profile, recent }) => {
        this.profileSubject.next(profile);
        this.recentXpSubject.next(recent);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  loadProfile(): Observable<UserProfileResponse> {
    return this.userApi.getProfile(this.currentUserId);
  }
}
