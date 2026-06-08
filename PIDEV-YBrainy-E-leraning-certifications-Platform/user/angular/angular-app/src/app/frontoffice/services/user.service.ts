import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, defer, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { getToken } from '../../auth/keycloak.service';
import { UserSessionService } from '../../tracking/user-session.service';

export interface UserProfile {
  userId: number;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  email: string;
  role: string;
  dateOfBirth?: string | null;
  profilePicture?: string | null;
  age?: number | null;
  address?: string | null;
  city?: string | null;
  country?: string | null;
  sex?: string | null;
  companyName?: string | null;
  enterpriseCompanyId?: string | null;
  enterpriseVerified?: boolean;
  enterpriseOnboardedAt?: string | null;
  faceBiometricRegistered?: boolean;
  streakDays?: number;
  lastLogin?: string | null;
  accountCreatedAt?: string | null;
  lastProfileUpdate?: string | null;
  lockedUntil?: string | null;
  reasonForBan?: string | null;
  banPeriod?: number | null;
  status?: 'SECURE' | 'WARNED' | 'SUSPENDED' | 'FLAGGED' | string | null;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(UserSessionService);
  private readonly usersApiBase = `${environment.apiBaseUrl}/api/users`;

  getCurrentUser(): Observable<UserProfile> {
    return defer(() => {
      const storedSession = this.session.get();
      const claims = this.decodeJwtClaims(getToken());
      const resolvers: Array<() => Observable<UserProfile>> = [];

      if (
        typeof storedSession?.userId === 'number' &&
        Number.isFinite(storedSession.userId) &&
        storedSession.userId > 0
      ) {
        resolvers.push(() =>
          this.http.get<UserProfile>(`${this.usersApiBase}/${storedSession.userId}`).pipe(
            tap((user) => this.refreshSession(user))
          )
        );
      }

      const email =
        storedSession?.email?.trim() ||
        this.claimAsString(claims, 'email');

      if (email) {
        resolvers.push(() =>
          this.http
            .get<UserProfile>(`${this.usersApiBase}/email/${encodeURIComponent(email)}`)
            .pipe(tap((user) => this.refreshSession(user)))
        );
      }

      const username =
        storedSession?.username?.trim() ||
        this.claimAsString(claims, 'preferred_username');

      if (username) {
        resolvers.push(() =>
          this.http
            .get<UserProfile>(`${this.usersApiBase}/username/${encodeURIComponent(username)}`)
            .pipe(tap((user) => this.refreshSession(user)))
        );
      }

      if (!resolvers.length) {
        return throwError(() => new Error('Unable to resolve current user identity.'));
      }

      return this.tryCurrentUserResolvers(resolvers);
    });
  }

  updateUser(userId: number, dto: Partial<UserProfile>): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.usersApiBase}/${userId}`, dto).pipe(
      tap((user) => this.refreshSession(user))
    );
  }

  registerFaceBiometric(userId: number, image: Blob): Observable<UserProfile> {
    const formData = new FormData();
    formData.append('image', image, 'face-biometric.png');

    return this.http.post<UserProfile>(`${this.usersApiBase}/${userId}/face-biometric`, formData).pipe(
      tap((user) => this.refreshSession(user))
    );
  }

  removeFaceBiometric(userId: number): Observable<UserProfile> {
    return this.http.delete<UserProfile>(`${this.usersApiBase}/${userId}/face-biometric`).pipe(
      tap((user) => this.refreshSession(user))
    );
  }

  changePassword(userId: number, newPassword: string, confirmPassword: string): Observable<void> {
    return this.http.put<void>(`${this.usersApiBase}/${userId}/password`, {
      newPassword,
      confirmPassword,
    });
  }

  deleteUser(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.usersApiBase}/${userId}`);
  }

  private refreshSession(user: UserProfile): void {
    if (!user?.userId || !user?.role) {
      return;
    }

    const current = this.session.get();
    this.session.set({
      userId: user.userId,
      role: user.role,
      email: user.email ?? current?.email,
      username: user.username ?? current?.username,
    });
  }

  private tryCurrentUserResolvers(
    resolvers: Array<() => Observable<UserProfile>>,
    index = 0
  ): Observable<UserProfile> {
    if (index >= resolvers.length) {
      return throwError(() => new Error('Unable to load current user profile.'));
    }

    return resolvers[index]().pipe(
      catchError(() => this.tryCurrentUserResolvers(resolvers, index + 1))
    );
  }

  private claimAsString(claims: Record<string, unknown> | null, key: string): string | null {
    const value = claims?.[key];
    if (typeof value !== 'string') return null;
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }

  private decodeJwtClaims(token: string | undefined | null): Record<string, unknown> | null {
    if (!token) return null;

    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const payload = this.normalizeBase64Url(parts[1]);
      return JSON.parse(atob(payload)) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private normalizeBase64Url(value: string): string {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
    const padding = (4 - (base64.length % 4)) % 4;
    return base64 + '='.repeat(padding);
  }
}
