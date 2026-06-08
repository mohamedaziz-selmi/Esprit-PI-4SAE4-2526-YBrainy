import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '@env/environment';

export const apiPrefixInterceptor: HttpInterceptorFn = (req, next) => {
  const url = req.url ?? '';

  const matchesApiPrefix = (value: string, prefix: string): boolean => {
    return value === prefix || value.startsWith(`${prefix}/`) || value.startsWith(`${prefix}?`);
  };

  // Let dev proxy (and same-origin deployments) handle YBRAINY microservice endpoints.
  const noPrefixApiPrefixes = [
    '/api/personalities',
    '/api/behaviors',
    '/api/partnerships',
    '/api/offers',
    '/api/applications',
    '/api/generate-application',
  ];
  if (noPrefixApiPrefixes.some((prefix) => matchesApiPrefix(url, prefix))) {
    return next(req);
  }

  // Only prefix same-origin relative API and uploads calls.
  if (url.startsWith('/api/') || url === '/api' || url.startsWith('/uploads/')) {
    const courseApiPrefixes = [
      '/api/courses',
      '/api/quizzes',
      '/api/enrollments',
      '/api/payments',
      '/api/learning-paths',
      '/api/instructor',
      '/api/students',
      '/api/ml',
      '/api/certificates',
    ];
    const courseBaseUrl = environment.courseApiBaseUrl || environment.apiBaseUrl;
    const targetBase = courseApiPrefixes.some((prefix) => url === prefix || url.startsWith(`${prefix}/`))
      ? courseBaseUrl
      : environment.forumApiUrl;
    const prefixed = targetBase.replace(/\/$/, '') + url;
    return next(req.clone({ url: prefixed }));
  }

  return next(req);
};
