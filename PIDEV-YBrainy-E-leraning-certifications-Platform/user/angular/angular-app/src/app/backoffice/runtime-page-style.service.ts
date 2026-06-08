import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class RuntimePageStyleService {
  private readonly attachedStyles = new Map<string, { element: HTMLLinkElement; count: number }>();

  constructor(@Inject(DOCUMENT) private readonly document: Document) {}

  attach(paths: string[]): () => void {
    const hrefs = Array.from(new Set(paths.map((path) => this.normalizeHref(path))));

    hrefs.forEach((href) => {
      const tracked = this.attachedStyles.get(href);
      if (tracked) {
        tracked.count += 1;
        return;
      }

      const existing = Array.from(
        this.document.head.querySelectorAll('link[data-runtime-page-style="true"]')
      ).find((link) => link.getAttribute('href') === href) as HTMLLinkElement | undefined;

      if (existing) {
        this.attachedStyles.set(href, { element: existing, count: 1 });
        return;
      }

      const link = this.document.createElement('link');
      link.rel = 'stylesheet';
      link.href = href;
      link.setAttribute('data-runtime-page-style', 'true');
      this.document.head.appendChild(link);
      this.attachedStyles.set(href, { element: link, count: 1 });
    });

    return () => {
      hrefs.forEach((href) => this.detach(href));
    };
  }

  private detach(href: string): void {
    const tracked = this.attachedStyles.get(href);
    if (!tracked) {
      return;
    }

    tracked.count -= 1;
    if (tracked.count > 0) {
      return;
    }

    tracked.element.remove();
    this.attachedStyles.delete(href);
  }

  private normalizeHref(path: string): string {
    if (/^https?:\/\//i.test(path)) {
      return path;
    }

    return path.startsWith('/') ? path : `/${path}`;
  }
}
