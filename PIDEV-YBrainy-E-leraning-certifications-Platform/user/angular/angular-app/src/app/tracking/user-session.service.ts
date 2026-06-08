import { Injectable } from '@angular/core';

export type SessionRole = 'ADMIN' | 'INSTRUCTOR' | 'STUDENT' | 'ENTERPRISE_USER' | string;

export interface UserSession {
  userId: number;
  role: SessionRole;
  email?: string;
  username?: string;
}

const STORAGE_KEY = 'bb_user_session_v1';

@Injectable({ providedIn: 'root' })
export class UserSessionService {
  get(): UserSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<UserSession> & { userId?: number | string };
      if (!parsed || !parsed.role) return null;

      const userId =
        typeof parsed.userId === 'number'
          ? parsed.userId
          : Number.parseInt(String(parsed.userId ?? ''), 10);

      if (!Number.isFinite(userId) || userId <= 0) return null;

      return {
        userId,
        role: String(parsed.role),
        email: typeof parsed.email === 'string' ? parsed.email : undefined,
        username: typeof parsed.username === 'string' ? parsed.username : undefined,
      };
    } catch {
      return null;
    }
  }

  set(session: UserSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
  }

  isLoggedIn(): boolean {
    return this.get() !== null;
  }

  isAdmin(): boolean {
    const s = this.get();
    return (s?.role ?? '').toUpperCase() === 'ADMIN';
  }

  // Mode management (UI state, not permission level)
  setMode(mode: 'STUDENT' | 'INSTRUCTOR' | 'ADMIN'): void {
    localStorage.setItem('ybrainy_user_mode', mode);
  }

  getMode(): 'STUDENT' | 'INSTRUCTOR' | 'ADMIN' {
    const storedMode = localStorage.getItem('ybrainy_user_mode');
    if (storedMode) {
      return storedMode as 'STUDENT' | 'INSTRUCTOR' | 'ADMIN';
    }
    // Default to user's actual role if no mode set
    const session = this.get();
    return (session?.role as 'STUDENT' | 'INSTRUCTOR' | 'ADMIN') || 'STUDENT';
  }

  canAccessMode(mode: string): boolean {
    const role = this.get()?.role;
    if (mode === 'STUDENT') return true; // Everyone can use student mode
    if (mode === 'INSTRUCTOR') return role === 'INSTRUCTOR' || role === 'ADMIN';
    if (mode === 'ADMIN') return role === 'ADMIN';
    return false;
  }

  getAvailableModes(): Array<'STUDENT' | 'INSTRUCTOR' | 'ADMIN'> {
    const role = this.get()?.role;
    const modes: Array<'STUDENT' | 'INSTRUCTOR' | 'ADMIN'> = ['STUDENT'];
    if (role === 'INSTRUCTOR' || role === 'ADMIN') modes.push('INSTRUCTOR');
    if (role === 'ADMIN') modes.push('ADMIN');
    return modes;
  }
}
