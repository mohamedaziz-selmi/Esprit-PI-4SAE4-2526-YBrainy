import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  email: string;
  name: string;
  role: string;
}

export interface AuthUser {
  email: string;
  name: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly tokenKey = 'yb_auth_token';
  private readonly userKey = 'yb_auth_user';
  private readonly loginUrl = `${environment.apiUrl}/auth/login`;

  constructor(private http: HttpClient) { }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.loginUrl, { email, password });
  }

  storeSession(response: LoginResponse): void {
    localStorage.setItem(this.tokenKey, response.token);
    localStorage.setItem(this.userKey, JSON.stringify({
      email: response.email,
      name: response.name,
      role: response.role
    } as AuthUser));
  }

  clearSession(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  getUser(): AuthUser | null {
    const raw = localStorage.getItem(this.userKey);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const exp = payload?.exp;
      if (!exp) return true;
      return Date.now() < exp * 1000;
    } catch {
      return false;
    }
  }
}
