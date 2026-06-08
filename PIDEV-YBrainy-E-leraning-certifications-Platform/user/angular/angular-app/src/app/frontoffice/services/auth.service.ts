import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

import { logout } from '../../auth/keycloak.service';
import { UserSessionService } from '../../tracking/user-session.service';
import { UserService } from './user.service';

export interface AuthUser {
  id: number;
  username: string;
  email: string;
  role: string;
  xp: number;
  level: number;
}

const STORAGE_KEY = 'forum_auth_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userSubject: BehaviorSubject<AuthUser | null>;
  readonly currentUser$: Observable<AuthUser | null>;

  constructor(
    private readonly session: UserSessionService,
    private readonly users: UserService
  ) {
    const sessionUser = this.sessionToAuthUser();
    const storedUser = this.readStoredUser();
    const initial = sessionUser ?? (this.session.isLoggedIn() ? storedUser : null);
    this.userSubject = new BehaviorSubject<AuthUser | null>(initial);
    this.currentUser$ = this.userSubject.asObservable();

    if (sessionUser) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sessionUser));
    }

    if (this.session.isLoggedIn()) {
      this.refreshCurrentUser();
    }
  }

  get currentUser(): AuthUser | null {
    return this.userSubject.value;
  }

  get currentUserId(): number | null {
    return this.session.get()?.userId ?? this.userSubject.value?.id ?? null;
  }

  isLoggedIn(): boolean {
    return this.session.isLoggedIn() || this.userSubject.value !== null;
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.clear();
    this.userSubject.next(null);
    void logout();
  }

  updateUser(partial: Partial<AuthUser>): void {
    const current = this.userSubject.value;
    if (!current) return;
    const updated = { ...current, ...partial };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    this.userSubject.next(updated);
  }

  refreshCurrentUser(): void {
    this.users.getCurrentUser().subscribe({
      next: (user) => {
        const mapped: AuthUser = {
          id: user.userId,
          username: user.username,
          email: user.email,
          role: user.role,
          xp: 0,
          level: 0,
        };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(mapped));
        this.userSubject.next(mapped);
      },
      error: () => {
        const fallback = this.sessionToAuthUser();
        if (!fallback) return;
        localStorage.setItem(STORAGE_KEY, JSON.stringify(fallback));
        this.userSubject.next(fallback);
      },
    });
  }

  private sessionToAuthUser(): AuthUser | null {
    const current = this.session.get();
    if (!current?.userId) return null;

    return {
      id: current.userId,
      username: current.username ?? current.email ?? `user-${current.userId}`,
      email: current.email ?? '',
      role: current.role,
      xp: 0,
      level: 0,
    };
  }

  private readStoredUser(): AuthUser | null {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored ? (JSON.parse(stored) as AuthUser) : null;
    } catch {
      return null;
    }
  }
}
