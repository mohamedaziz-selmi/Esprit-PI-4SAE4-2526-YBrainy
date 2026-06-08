import { environment } from '../../../environments/environment';

export function courseMediaUrl(raw: string | undefined | null): string {
  const value = String(raw ?? '').trim();
  if (!value) return '';
  if (value.startsWith('http://') || value.startsWith('https://') || value.startsWith('assets/')) return value;

  const path = value.startsWith('/api/courses/files/')
    ? value
    : `/api/courses/files/${value.replace(/^\/+/, '')}`;

  return `${(environment.courseApiBaseUrl || environment.apiBaseUrl).replace(/\/$/, '')}${path}`;
}
