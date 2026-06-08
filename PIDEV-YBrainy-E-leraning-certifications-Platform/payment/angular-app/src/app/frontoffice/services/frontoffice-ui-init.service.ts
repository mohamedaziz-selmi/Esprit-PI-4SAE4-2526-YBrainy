import { Injectable, NgZone } from '@angular/core';

declare var Swiper: any;
declare var $: any;

/**
 * Minimal UI initializers for pages rendered from static templates.
 * (Keeps the same look/behavior for common widgets like Swiper/accordions.)
 */
@Injectable({ providedIn: 'root' })
export class FrontofficeUiInitService {
  private hubspotEmbedScriptPromise: Promise<void> | null = null;
  private hubspotV2ScriptPromise: Promise<void> | null = null;

  constructor(private ngZone: NgZone) {}

  initAfterDomPaint(): void {
    // Run outside Angular to avoid triggering change detection repeatedly for animation libs.
    this.ngZone.runOutsideAngular(() => {
      setTimeout(() => {
        try {
          this.initCommonSwipers();
          this.initBlueCardsSwiper();
          this.initAccordions();
          this.initHubspotFormsEmbed();
          this.initHubspotV2Forms();
          this.initCommonTabs();
          this.initExperienceTabs();
        } catch (e) {
          // eslint-disable-next-line no-console
          console.warn('Frontoffice UI init error:', e);
        }
      }, 0);
    });
  }

  private initCommonSwipers(): void {
    if (typeof Swiper === 'undefined') return;

    if (document.querySelector('.partnerSwiper')) {
      new Swiper('.partnerSwiper', {
        slidesPerView: 2.2,
        speed: 3000,
        loop: true,
        autoplay: { delay: 0 },
        breakpoints: {
          1201: { slidesPerView: 5 },
          991: { slidesPerView: 4 },
          481: { slidesPerView: 3 },
        },
      });
    }

    if (document.querySelector('.successSwiper')) {
      new Swiper('.successSwiper', {
        slidesPerView: 1,
        speed: 800,
        spaceBetween: 20,
        watchSlidesProgress: true,
        navigation: { nextEl: '.successSwiperNext', prevEl: '.successSwiperPrev' },
        breakpoints: {
          1201: { slidesPerView: 3, spaceBetween: 60 },
          641: { slidesPerView: 2, spaceBetween: 40 },
        },
      });
    }

    if (document.querySelector('.trustSwiper')) {
      const trustSwiper = new Swiper('.trustSwiper', {
        slidesPerView: 1.8,
        speed: 3000,
        spaceBetween: 20,
        loop: true,
        observer: true,
        observeParents: true,
        autoplay: { delay: 1, disableOnInteraction: false, pauseOnMouseEnter: false },
        watchSlidesProgress: true,
        watchOverflow: true,
        breakpoints: {
          1201: { slidesPerView: 5 },
          991: { slidesPerView: 4 },
          481: { slidesPerView: 3 },
        },
      });

      // Make autoplay resume when tab becomes visible again
      setTimeout(() => {
        try {
          trustSwiper.update();
          if (document.visibilityState === 'visible') trustSwiper.autoplay.start();
          else {
            const onVis = () => {
              if (document.visibilityState === 'visible') {
                trustSwiper.autoplay.start();
                document.removeEventListener('visibilitychange', onVis);
              }
            };
            document.addEventListener('visibilitychange', onVis);
          }
        } catch {
          /* ignore */
        }
      }, 300);
    }
  }

  private initBlueCardsSwiper(): void {
    if (typeof Swiper === 'undefined') return;

    const el = document.querySelector('.blueCardSwiper') as HTMLElement | null;
    if (!el) return;

    // Avoid duplicate init when navigating back/forth
    if (el.classList.contains('swiper-initialized')) return;

    new Swiper('.blueCardSwiper', {
      slidesPerView: 1,
      speed: 800,
      spaceBetween: 40,
      breakpoints: {
        1201: { slidesPerView: 3 },
        901: { slidesPerView: 2 },
      },
      navigation: {
        nextEl: '.blueCardNext',
        prevEl: '.blueCardPrev',
      },
      watchSlidesProgress: true,
    });
  }

  private initHubspotFormsEmbed(): void {
    const frames = document.querySelectorAll('.hs-form-frame');
    if (!frames.length) return;

    // This script is referenced by the mirrored template, but it won't execute when injected via [innerHTML].
    // Load it once at runtime so it can render forms inside `.hs-form-frame` containers.
    this.ensureHubspotEmbedScriptLoaded().catch((e) => {
      // eslint-disable-next-line no-console
      console.warn('HubSpot forms embed failed to load:', e);
    });
  }

  private ensureHubspotEmbedScriptLoaded(): Promise<void> {
    const w = window as any;

    // Already initialised by a previous page.
    if (w && w.__HS__FORMS__EMBED__) return Promise.resolve();

    if (this.hubspotEmbedScriptPromise) return this.hubspotEmbedScriptPromise;

    const src = 'assets/frontoffice/js-eu1.hsforms.net/forms/embed/24948776.js';

    this.hubspotEmbedScriptPromise = new Promise<void>((resolve, reject) => {
      const existing = document.querySelector(`script[src="${src}"]`) as HTMLScriptElement | null;
      if (existing) {
        // If it already exists, assume it will (or already did) load.
        if (w && w.__HS__FORMS__EMBED__) resolve();
        else existing.addEventListener('load', () => resolve(), { once: true });
        existing.addEventListener('error', () => reject(new Error('Failed to load HubSpot embed script')), { once: true });
        return;
      }

      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load HubSpot embed script'));
      document.head.appendChild(script);
    });

    return this.hubspotEmbedScriptPromise;
  }

  private initHubspotV2Forms(): void {
    // About template uses HubSpot v2 embed (v2.js + inline hbspt.forms.create(...)).
    // Scripts inside [innerHTML] won't execute, so we detect the config and run it here.
    const containers = document.querySelectorAll<HTMLElement>('.contact-form');
    if (!containers.length) return;

    containers.forEach((container) => {
      if (container.dataset['foHsV2Init'] === '1') return;
      if (container.querySelector('iframe, form')) return;

      const scripts = Array.from(container.querySelectorAll<HTMLScriptElement>('script'));
      const hasV2ScriptTag = scripts.some((s) => (s.getAttribute('src') ?? '').includes('/forms/embed/v2.js'));
      const inline = scripts.map((s) => s.textContent ?? '').join('\n');
      const hasCreateCall = /hbspt\.forms\.create\s*\(/.test(inline);
      if (!hasV2ScriptTag && !hasCreateCall) return;

      const portalId = this.matchHubspotValue(inline, 'portalId');
      const formId = this.matchHubspotValue(inline, 'formId');
      const region = this.matchHubspotValue(inline, 'region');
      if (!portalId || !formId) return;

      container.innerHTML = '';
      const target = document.createElement('div');
      target.id = `fo-hs-v2-${formId}`;
      container.appendChild(target);

      container.dataset['foHsV2Init'] = '1';

      this.ensureHubspotV2ScriptLoaded()
        .then(() => {
          const w = window as any;
          if (!w?.hbspt?.forms?.create) return;
          w.hbspt.forms.create({
            portalId,
            formId,
            ...(region ? { region } : {}),
            target: `#${target.id}`,
          });
        })
        .catch((e) => {
          // eslint-disable-next-line no-console
          console.warn('HubSpot v2 forms embed failed to load:', e);
        });
    });
  }

  private ensureHubspotV2ScriptLoaded(): Promise<void> {
    const w = window as any;
    if (w?.hbspt?.forms?.create) return Promise.resolve();
    if (this.hubspotV2ScriptPromise) return this.hubspotV2ScriptPromise;

    const src = 'assets/frontoffice/js-eu1.hsforms.net/forms/embed/v2.js';

    this.hubspotV2ScriptPromise = new Promise<void>((resolve, reject) => {
      const existing = document.querySelector(`script[src="${src}"]`) as HTMLScriptElement | null;
      if (existing) {
        existing.addEventListener('load', () => resolve(), { once: true });
        existing.addEventListener('error', () => reject(new Error('Failed to load HubSpot v2 embed script')), { once: true });
        return;
      }

      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load HubSpot v2 embed script'));
      document.head.appendChild(script);
    });

    return this.hubspotV2ScriptPromise;
  }

  private matchHubspotValue(source: string, key: string): string | null {
    const re = new RegExp(`${key}\\s*:\\s*["']([^"']+)["']`, 'i');
    const m = source.match(re);
    return m?.[1] ?? null;
  }

  private initCommonTabs(): void {
    // Common template tabs: <ul class="tabs ..."><li data-tab="..."> + <div class="tab_container"><div id="..."
    const root = document.body;

    // Ensure a visible default tab if none is active.
    root.querySelectorAll<HTMLElement>('.tab_container').forEach((container) => {
      const contents = Array.from(container.querySelectorAll<HTMLElement>(':scope > .tab_content'));
      if (!contents.length) return;
      if (contents.some((c) => c.classList.contains('active'))) return;
      contents[0].classList.add('active');
    });

    if (typeof $ === 'undefined') return;
    $(document).off('click.foTabs');
    $(document).on('click.foTabs', '.comm-tab-box.tabs li[data-tab]', function (this: any, e: any) {
      e.preventDefault?.();
      const li = this as HTMLElement;
      const tabId = li.getAttribute('data-tab');
      if (!tabId) return;

      const tabWrap = li.closest('.comm-tab-wrap') as HTMLElement | null;
      const scope = tabWrap ?? document;

      // Activate clicked tab
      li.parentElement?.querySelectorAll('li').forEach((n) => n.classList.remove('active'));
      li.classList.add('active');

      const container = scope.querySelector('.tab_container') as HTMLElement | null;
      if (!container) return;
      container.querySelectorAll('.tab_content').forEach((c) => c.classList.remove('active'));

      const target = container.querySelector<HTMLElement>(`#${CSS.escape(tabId)}`);
      if (target) target.classList.add('active');
    });
  }

  private initExperienceTabs(): void {
    // About page "exp-tabs-wrap" controls the left `.exp-box-txt` blocks.
    const expBox = document.querySelector('.exp-box') as HTMLElement | null;
    if (!expBox) return;

    const tabs = Array.from(expBox.querySelectorAll<HTMLElement>('.exp-tabs-wrap .exp-tab'));
    const blocks = Array.from(expBox.querySelectorAll<HTMLElement>('.exp-box-left .exp-box-txt'));
    if (!tabs.length || !blocks.length) return;

    const activate = (idx: number) => {
      tabs.forEach((t) => t.classList.remove('active'));
      blocks.forEach((b) => b.classList.remove('active'));
      if (tabs[idx]) tabs[idx].classList.add('active');
      if (blocks[idx]) blocks[idx].classList.add('active');
    };

    const activeIdx = tabs.findIndex((t) => t.classList.contains('active'));
    activate(activeIdx >= 0 ? activeIdx : 0);

    tabs.forEach((tab, idx) => {
      if (tab.dataset['foExpBind'] === '1') return;
      tab.dataset['foExpBind'] = '1';
      tab.addEventListener('mouseenter', () => activate(idx));
      tab.addEventListener('click', (e) => {
        e.preventDefault();
        activate(idx);
      });
    });
  }

  private initAccordions(): void {
    if (typeof $ === 'undefined') return;
    if (!document.querySelector('.acc-item')) return;

    // Initial state: first panel open (matches template behavior)
    $('.acc-item').find('.panel').slideUp();
    $('.acc-item').eq(0).find('.panel').slideDown();
    $('.acc-container').each((_i: number, e: any) => $(e).find('.panel:first').slideDown());

    // Avoid duplicate handlers if navigating back/forth
    $(document).off('click.foAccordion');
    $(document).on('click.foAccordion', '.acc-item', function (this: any) {
      const accContainer = $(this).closest('.acc-container');
      accContainer.find('.acc-item').not(this).removeClass('active').find('.panel').slideUp();
      $(this).toggleClass('active').find('.panel').slideToggle();
    });
  }
}


