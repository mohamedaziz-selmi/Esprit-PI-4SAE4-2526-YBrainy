import { Injectable } from '@angular/core';
import { environment } from '@env/environment';

@Injectable({ providedIn: 'root' })
export class MediaUrlService {
  private readonly apiOrigin = environment.forumApiUrl;

  url(input?: string | null): string {
    const raw = (input ?? '').trim();
    if (!raw) return '';

    if (/^https?:\/\//i.test(raw)) return raw;

    if (raw.startsWith('//')) return 'http:' + raw;

    if (raw.startsWith('/')) return this.apiOrigin + raw;

    return this.apiOrigin + '/' + raw;
  }
}
