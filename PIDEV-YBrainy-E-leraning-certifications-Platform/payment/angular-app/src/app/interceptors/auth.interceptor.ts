import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private authService: AuthService) { }

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.authService.getToken();

    if (!token) {
      return next.handle(req);
    }

    // Do not attach JWT to login endpoint.
    if (req.url.includes('/api/auth/login')) {
      return next.handle(req);
    }

    // Attach JWT only to local backend API calls.
    const isLocalApiCall =
      req.url.startsWith('/api/') ||
      /^https?:\/\/localhost(?::\d+)?\/api\//.test(req.url);

    if (!isLocalApiCall) {
      return next.handle(req);
    }

    return next.handle(
      req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
    );
  }
}
