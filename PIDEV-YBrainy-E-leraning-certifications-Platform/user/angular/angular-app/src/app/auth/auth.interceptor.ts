import { HttpInterceptorFn } from '@angular/common/http';
import { from, switchMap } from 'rxjs';
import { getToken, updateToken } from './keycloak.service';

function getRequestPath(url: string): string {
  try {
    return new URL(url, window.location.origin).pathname;
  } catch {
    return url.split('?')[0] ?? url;
  }
}

function isPublicRequest(method: string, url: string): boolean {
  const path = getRequestPath(url);
  const isGet = method.toUpperCase() === 'GET';

  return (
    (isGet && path.startsWith('/api/courses')) ||
    (isGet && path.startsWith('/api/ml')) ||
    (isGet && path.startsWith('/api/quizzes')) ||
    (isGet && path.startsWith('/api/enrollments')) ||
    (isGet && path.startsWith('/api/students')) ||
    (isGet && path.startsWith('/api/certificates/student')) ||
    (method.toUpperCase() === 'POST' && path === '/api/courses/search/ai')
  );
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (isPublicRequest(req.method, req.url)) {
    return next(req.clone({ headers: req.headers.delete('Authorization') }));
  }

  return from(updateToken().catch(() => undefined)).pipe(
    switchMap(() => {
      const token = getToken();
      if (!token) return next(req);

      return next(
        req.clone({
          setHeaders: { Authorization: `Bearer ${token}` },
        })
      );
    })
  );
};
