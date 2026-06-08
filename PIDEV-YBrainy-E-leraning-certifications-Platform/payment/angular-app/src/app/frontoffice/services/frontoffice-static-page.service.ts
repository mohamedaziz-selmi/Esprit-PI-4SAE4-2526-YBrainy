import { Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

export interface FrontofficeStaticPageResult {
  bodyClass: string;
  mainHtml: SafeHtml;
}

/**
 * Loads a mirrored static HTML page from /assets, extracts <body class> and <main>,
 * rewrites relative URLs to work inside the Angular SPA, and returns it as SafeHtml.
 */
@Injectable({ providedIn: 'root' })
export class FrontofficeStaticPageService {
  private readonly cache = new Map<string, FrontofficeStaticPageResult>();

  constructor(private sanitizer: DomSanitizer) {}

  async load(url: string, mainSelector: string = 'main'): Promise<FrontofficeStaticPageResult> {
    const cached = this.cache.get(url);
    if (cached) return cached;

    const res = await fetch(url, { cache: 'force-cache' });
    if (!res.ok) {
      throw new Error(`Failed to load template: ${url} (${res.status})`);
    }

    const htmlText = await res.text();
    const doc = new DOMParser().parseFromString(htmlText, 'text/html');

    const bodyClass = doc.body?.getAttribute('class') ?? '';
    const main = doc.querySelector(mainSelector) ?? doc.querySelector('main');
    if (!main) {
      throw new Error(`No <main> found in template: ${url}`);
    }

    // Rewrite relative src/href/etc so assets load correctly from /assets/...
    this.rewriteRelativeUrls(main as HTMLElement, url);

    // Map a few common template links back into SPA routes (unified Angular navigation).
    this.rewriteTemplateLinksToSpaRoutes(main as HTMLElement);

    const safe = this.sanitizer.bypassSecurityTrustHtml((main as HTMLElement).outerHTML);
    const result: FrontofficeStaticPageResult = { bodyClass, mainHtml: safe };
    this.cache.set(url, result);
    return result;
  }

  private rewriteRelativeUrls(root: HTMLElement, pageUrl: string): void {
    const absoluteBase = new URL(pageUrl, window.location.origin + '/').href;
    const attrs = ['src', 'href', 'poster'];

    root.querySelectorAll<HTMLElement>('*').forEach((el) => {
      for (const attr of attrs) {
        const raw = el.getAttribute(attr);
        if (!raw) continue;

        const val = raw.trim();
        if (!val) continue;
        if (
          val.startsWith('#') ||
          val.startsWith('mailto:') ||
          val.startsWith('tel:') ||
          val.startsWith('data:') ||
          val.startsWith('http://') ||
          val.startsWith('https://')
        ) {
          continue;
        }

        // HTTrack templates often contain javascript:void(0); keep as-is.
        if (val.toLowerCase().startsWith('javascript:')) {
          continue;
        }

        try {
          const resolved = new URL(val, absoluteBase);
          const newVal = resolved.pathname + resolved.search + resolved.hash;
          el.setAttribute(attr, newVal);
        } catch {
          // Ignore invalid URLs
        }
      }
    });
  }

  private rewriteTemplateLinksToSpaRoutes(root: HTMLElement): void {
    const routeMap: Array<{ match: RegExp; to: string }> = [
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/index\.html$/i, to: '/' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/services\/index\.html$/i, to: '/services' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/about-ciklum\/index\.html$/i, to: '/about' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/all-resources\/index\.html$/i, to: '/resources' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/ebooks\/index\.html$/i, to: '/courses' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/blogs\/index\.html$/i, to: '/forum' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/videos\/index\.html$/i, to: '/calendar' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/tag\/payments\/index\.html$/i, to: '/payment' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/case-studies\/payment-gateway-development\/index\.html$/i, to: '/payment' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/travel-hospitality\/index\.html$/i, to: '/payment' },
      { match: /\/assets\/frontoffice\/www\.ciklum\.com\/industries\/travel-hospitality\/index\.html$/i, to: '/payment' },
    ];

    root.querySelectorAll<HTMLAnchorElement>('a[href]').forEach((a) => {
      const href = (a.getAttribute('href') ?? '').trim();
      if (!href || href.startsWith('#') || href.toLowerCase().startsWith('javascript:')) return;

      let pathname = href;
      let search = '';
      let hash = '';
      try {
        const u = new URL(href, window.location.origin);
        pathname = u.pathname;
        search = u.search;
        hash = u.hash;
      } catch {
        // ignore parse errors; fall back to raw href matching
      }

      for (const { match, to } of routeMap) {
        if (match.test(pathname)) {
          a.setAttribute('href', to + search + hash);
          // Avoid opening new tabs for SPA links
          a.removeAttribute('target');
          a.removeAttribute('rel');
          return;
        }
      }

      // Dynamic: map all mirrored Services detail pages to SPA route /services/:slug
      // Handles:
      // - /assets/frontoffice/www.ciklum.com/services/<slug>/index.html
      // - /assets/frontoffice/www.ciklum.com/services/<slug>/index*.html
      // - /assets/frontoffice/www.ciklum.com/services/<slug>/
      // - /services/<slug>/index*.html   (some templates use absolute /services paths)
      const svcAsset = pathname.match(
        /^\/assets\/frontoffice\/www\.ciklum\.com\/services\/([^\/?#]+)(?:\/index[^\/?#]*\.html)?\/?$/i
      );
      const svcAbs = pathname.match(/^\/services\/([^\/?#]+)(?:\/index[^\/?#]*\.html)?\/?$/i);
      const slug = (svcAsset?.[1] ?? svcAbs?.[1] ?? '').trim();
      if (slug) {
        a.setAttribute('href', `/services/${slug}${search}${hash}`);
        a.removeAttribute('target');
        a.removeAttribute('rel');
        return;
      }
    });
  }
}


