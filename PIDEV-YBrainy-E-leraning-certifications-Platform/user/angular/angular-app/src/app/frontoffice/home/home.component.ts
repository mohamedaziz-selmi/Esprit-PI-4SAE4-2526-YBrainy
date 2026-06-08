import { Component, AfterViewInit, OnDestroy, NgZone } from '@angular/core';

declare var gsap: any;
declare var ScrollTrigger: any;
declare var SplitText: any;
declare var Swiper: any;
declare var Lenis: any;
declare var lottie: any;
declare var $: any;

@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  host: { style: 'display:block' },
})
export class HomeComponent implements AfterViewInit, OnDestroy {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
  private lenis: any;
  private animObserver: IntersectionObserver | null = null;
  private visibilityFallbackTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private ngZone: NgZone) {}

  ngAfterViewInit(): void {
    // Run outside Angular zone for better performance with animation libs
    this.ngZone.runOutsideAngular(() => {
      setTimeout(() => this.bootHomeExperience(), 100);
    });
  }

  ngOnDestroy(): void {
    try {
      if (typeof ScrollTrigger !== 'undefined') {
        ScrollTrigger.getAll().forEach((st: any) => st.kill());
      }
      if (this.lenis) {
        this.lenis.destroy();
        this.lenis = null;
      }
      if (this.animObserver) {
        this.animObserver.disconnect();
        this.animObserver = null;
      }
      if (this.visibilityFallbackTimer) {
        clearTimeout(this.visibilityFallbackTimer);
        this.visibilityFallbackTimer = null;
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('Cleanup error:', e);
    }
  }

  private bootHomeExperience(): void {
    try {
      this.initAllAnimations();
      this.scheduleVisibilityFallback();
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('Home animation bootstrap failed, falling back to static view:', e);
      this.revealStaticHomeView();
    }
  }

  private initAllAnimations(): void {
    try {
      this.initLenis();
      this.initLoadingAnimation();
      this.initBinaryCanvas();
      this.initBannerHover();
      this.initScrollAnimations();
      this.initSwipers();
      this.initIndustrySection();
      this.initLottieAnimations();
      this.initFadeAnimations();
      this.initAccordions();
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('Animation init error:', e);
    }
  }

  private scheduleVisibilityFallback(): void {
    if (this.visibilityFallbackTimer) {
      clearTimeout(this.visibilityFallbackTimer);
    }

    this.visibilityFallbackTimer = setTimeout(() => {
      const mainHead = document.querySelector('.main-head') as HTMLElement | null;
      const bannerBottom = document.querySelector('.banner-bottom') as HTMLElement | null;
      const hiddenMainHead = mainHead ? getComputedStyle(mainHead).opacity === '0' : true;
      const hiddenBannerBottom = bannerBottom ? getComputedStyle(bannerBottom).opacity === '0' : true;

      if (hiddenMainHead && hiddenBannerBottom) {
        this.revealStaticHomeView();
      }
    }, 2800);
  }

  private revealStaticHomeView(): void {
    const showSelectors = ['.home-header', '.main-head', '.banner-bottom', '.fundamental-wrap .card-txt'];
    const resetTransformSelectors = ['.fundamental-head', '.fundamentals-grid', '.fundamental-wrap .comm-card'];

    showSelectors.forEach((selector) => {
      document.querySelectorAll<HTMLElement>(selector).forEach((el) => {
        el.style.opacity = '1';
      });
    });

    resetTransformSelectors.forEach((selector) => {
      document.querySelectorAll<HTMLElement>(selector).forEach((el) => {
        el.style.transform = 'none';
      });
    });

    document.querySelectorAll<HTMLElement>('.main-circle-wrap').forEach((el) => {
      el.style.display = 'none';
    });
  }

  private initLenis(): void {
    if (typeof Lenis === 'undefined') return;

    this.lenis = new Lenis({ smooth: true });

    // Drive Lenis ONLY through GSAP ticker (single update loop, no duplicate RAF)
    if (typeof gsap !== 'undefined') {
      gsap.ticker.add((time: number) => {
        if (this.lenis) {
          this.lenis.raf(time * 1000);
        }
      });
      gsap.ticker.lagSmoothing(0);
    } else {
      // Fallback if GSAP is not loaded: single RAF loop
      const raf = (time: number) => {
        if (this.lenis) {
          this.lenis.raf(time);
          requestAnimationFrame(raf);
        }
      };
      requestAnimationFrame(raf);
    }

    // Smooth scroll on anchor clicks
    document.querySelectorAll('a[href^="#"]').forEach((anchor: any) => {
      anchor.addEventListener('click', (e: Event) => {
        e.preventDefault();
        const targetId = (anchor as HTMLAnchorElement).getAttribute('href');
        if (targetId && targetId !== '#') {
          const targetElement = document.querySelector(targetId);
          if (targetElement) {
            this.lenis.scrollTo(targetElement, {
              duration: 1,
              easing: (t: number) => 1 - Math.pow(1 - t, 3),
              offset: 0,
            });
          }
        }
      });
    });
  }

  private initLoadingAnimation(): void {
    if (typeof gsap === 'undefined') return;

    gsap.registerPlugin(ScrollTrigger, SplitText);

    // Stop lenis during loading
    if (this.lenis) this.lenis.stop();

    gsap.to('.tl-circle', {
      opacity: 1,
      scale: 1,
      duration: 1.5,
      ease: 'power2.out',
    });
    gsap.to('.loading-text', { opacity: 1, duration: 0 });

    setTimeout(() => {
      gsap.to('.tl-circle', {
        width: screen.width > 1024 ? '52%' : '130%',
        duration: 1,
        top: screen.width > 640 ? '-16%' : '10vw',
      });
      gsap.to('.loading-text', { opacity: 0, duration: 1 });
      gsap.to('.cross-anim', { opacity: 1, duration: 1 });
      gsap.to('.home-header,.main-head,.banner-bottom', { opacity: 1, duration: 1 });
      gsap.from('.main-head,.banner-bottom', { y: 100, duration: 1 });
      gsap.to('.tl-circle video', { width: '60%', filter: 'blur(40px)', duration: 1 });
      gsap.to('.cross-anim-1,.cross-anim-2,.cross-anim-3', {
        x: 0,
        y: 0,
        opacity: 1,
        delay: 0.8,
        duration: 1,
        ease: 'back.out(1.5)',
      });

      if (this.lenis) this.lenis.start();
    }, 2000);
  }

  private initBinaryCanvas(): void {
    const binaryCanvases: any[] = [];

    function initCanvas(canvas: HTMLCanvasElement) {
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      const parentElem = canvas.parentElement;
      if (!parentElem) return;

      canvas.width = parentElem.clientWidth;
      canvas.height = parentElem.clientHeight;

      const fontSize = 7;
      const columns = Math.floor(canvas.width / fontSize);
      const rows = Math.floor(canvas.height / fontSize);

      ctx.font = `${fontSize}px Arial`;
      ctx.fillStyle = '#fff';

      const binChars = ['0', '1'];
      const bits: any[] = [];

      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < columns; c++) {
          const value = binChars[Math.floor(Math.random() * binChars.length)];
          bits.push({ x: c * fontSize, y: r * fontSize, value });
          ctx.fillText(value, c * fontSize, r * fontSize + fontSize);
        }
      }

      binaryCanvases.push({ canvas, ctx, bits, fontSize, bitWidth: fontSize, bitHeight: fontSize });
    }

    function animateBinary() {
      const fps = 3;
      const interval = 1000 / fps;
      let then = Date.now();

      function loop() {
        requestAnimationFrame(loop);
        const now = Date.now();
        const delta = now - then;

        if (delta > interval) {
          for (const { canvas, ctx, bits, bitWidth, bitHeight } of binaryCanvases) {
            const rect = canvas.getBoundingClientRect();
            if (rect.bottom < 0 || rect.top > window.innerHeight) continue;

            for (let i = 0; i < bits.length * 0.01; i++) {
              const bit = bits[Math.floor(Math.random() * bits.length)];
              bit.value = bit.value === '1' ? '0' : '1';
              ctx.clearRect(bit.x, bit.y, bitWidth, bitHeight);
              ctx.fillText(bit.value, bit.x, bit.y + bitHeight);
            }
          }
          then = now - (delta % interval);
        }
      }
      loop();
    }

    document.querySelectorAll('.binary-canvas').forEach((c: any) => initCanvas(c));
    animateBinary();
  }

  private initBannerHover(): void {
    const mainWrap = document.querySelector('.banner-wrap') as HTMLElement;
    const mainChildVid = document.querySelector('.tl-circle video') as HTMLElement;
    if (!mainWrap || !mainChildVid) return;

    let targetX = 0,
      targetY = 0,
      currentX = 0,
      currentY = 0;

    function animate() {
      const dx = targetX - currentX;
      const dy = targetY - currentY;

      if (Math.abs(dx) < 0.1 && Math.abs(dy) < 0.1) {
        currentX = targetX;
        currentY = targetY;
      } else {
        currentX += dx * 0.08;
        currentY += dy * 0.08;
      }

      mainChildVid.style.transform = `translate(${currentX}px, ${currentY}px)`;
      requestAnimationFrame(animate);
    }
    animate();

    mainWrap.addEventListener('mousemove', (e: MouseEvent) => {
      const rect = mainWrap.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      targetX = (x / rect.width - 0.5) * 420;
      targetY = (y / rect.height - 0.5) * 420;
    });

    mainWrap.addEventListener('mouseleave', () => {
      targetX = 0;
      targetY = 0;
    });
  }

  private initScrollAnimations(): void {
    if (typeof gsap === 'undefined' || typeof ScrollTrigger === 'undefined') return;

    // Trust section scroll animation
    const btl = gsap.timeline({
      scrollTrigger: {
        trigger: '.tl-wrap',
        start: '0% 100%',
        end: '0% -30%',
        scrub: 0,
      },
    });
    btl.to('.tl-circle video', { width: '100%', filter: 'blur(0px)' }, 0).to('.tl-circle .ring', { opacity: 1 }, 0);

    if (screen.width > 1024) {
      gsap.timeline({
        scrollTrigger: {
          trigger: '.tl-wrap',
          start: '0% 0%',
          end: '100% 0%',
          scrub: 0,
          pin: '.tl-wrap',
          pinType: 'fixed',
        },
      });

      btl.to(
        '.tl-circle',
        {
          top: ($('.tl-swiper').position()?.top || 0) - 50,
          y: '-100%',
          rotate: 180,
          width: '83%',
        },
        0
      ).to('.main-logo', { scale: 0, opacity: 0 }, 0);
    } else {
      const splitbt = SplitText.create('.tl-txt p', { type: 'words' });
      const btlMob = gsap.timeline({
        scrollTrigger: {
          trigger: '.tl-wrap',
          start: '0% 80%',
          end: '0% 80%',
          toggleActions: 'play none none reverse',
        },
      });
      btlMob
        .to(
          '.tl-circle',
          {
            width: screen.width > 576 ? '200%' : '290%',
            top: '-25%',
          },
          0
        )
        .to('.main-logo', { scale: 0, opacity: 0, duration: 1.2 }, 0)
        .to('.tl-circle', { opacity: 0, duration: 1.2 }, 0)
        .to('.tl-circle .ring', { opacity: 1, duration: 1.2 }, 0)
        .from(splitbt.words, { y: '50', opacity: 0, stagger: 0.05, duration: 1 }, 0);
    }

    // Fundamental section (matches template home.js)
    const ftl = gsap.timeline({
      scrollTrigger: {
        trigger: '.fundamental-section',
        start: '0% -40%',
        end: '0% -180%',
        scrub: 0,
      },
    });

    if (screen.width > 1024) {
      gsap
        .timeline({
          scrollTrigger: {
            trigger: '.fundamental-section',
            start: '0% 100%',
            end: '0% 0%',
            scrub: 0,
          },
        })
        .to('.tl-circle .ring', { opacity: 0 }, 0)
        .to(
          '.tl-circle',
          {
            scale: 0.7,
            top: '40%',
            rotate: 0,
            y: 0,
          },
          0
        );

      gsap.timeline({
        scrollTrigger: {
          trigger: '.fundamental-wrap',
          start: '0% 0%',
          end: '0% -200%',
          scrub: 0,
          pin: '.fundamental-wrap',
          pinType: 'fixed',
          invalidateOnRefresh: true,
        },
      });

      ftl
        .to('.fundamental-head', { opacity: 0, y: '-100%' }, 0)
        .to('.fundamentals-grid', { y: '-20%' }, 0)
        .to('.fundamental-wrap .card-txt', { opacity: 1 }, 0)
        .to('.fundamental-wrap .comm-card', { x: 0, y: 0 }, 0)
        .to(
          '.tl-circle',
          {
            top: screen.width > 1200 ? '-120%' : '-100%',
            rotate: 90,
          },
          0
        );

      gsap
        .timeline({
          scrollTrigger: {
            trigger: '.eng-section',
            start: '0% 80%',
            end: '0% 20%',
            scrub: 0,
          },
        })
        .to('.tl-circle .ring', { opacity: 1 }, 0)
        .to(
          '.tl-circle',
          {
            scale: 0.7,
            top: '-70%',
          },
          0
        );
    } else {
      gsap
        .timeline({
          scrollTrigger: {
            trigger: '.eng-section',
            start: '0% 80%',
            end: '0% 20%',
            scrub: 0,
          },
        })
        .to('.tl-circle', { width: '90%', opacity: 0.2 }, 0)
        .to('.tl-circle .ring', { opacity: 0 }, 0);

      gsap.timeline({
        scrollTrigger: {
          trigger: '.success-section',
          start: '0% 80%',
          end: '0% 20%',
          scrub: 0,
        },
      }).to('.tl-circle', { width: '0%', opacity: 0 }, 0);
    }

    if (screen.width > 1024) {
      gsap.timeline({
        scrollTrigger: {
          trigger: '.success-section',
          start: '0% 80%',
          end: '0% 0%',
          scrub: 0,
        },
      }).to('.tl-circle', { opacity: 0, scale: 0, display: 'none' }, 0);
    }

    // Pre-footer circle
    gsap
      .timeline({
        scrollTrigger: {
          trigger: '.pfHome',
          start: '0% 130%',
          end: '0% 75%',
          scrub: 0,
        },
      })
      .from('.pf-circle', { scale: 0.5 }, 0);
  }

  private initSwipers(): void {
    if (typeof Swiper === 'undefined') return;

    // Trust brand swiper
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

    setTimeout(() => {
      try {
        trustSwiper.update();
        if (document.visibilityState === 'visible') {
          trustSwiper.autoplay.start();
        } else {
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
    }, 600);

    if (typeof ScrollTrigger !== 'undefined') {
      ScrollTrigger.addEventListener('refresh', () => {
        try {
          trustSwiper.update();
          trustSwiper.autoplay.start();
        } catch {
          /* ignore */
        }
      });
      ScrollTrigger.refresh();
    }

    // Success stories swiper
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

    // Partner swiper
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

    // Blue hover cards swiper
    new Swiper('.swiperHomeCards', {
      slidesPerView: 1,
      speed: 800,
      spaceBetween: 40,
      breakpoints: {
        1201: { slidesPerView: 3 },
        901: { slidesPerView: 2 },
      },
      navigation: {
        nextEl: '.homeCardNext',
        prevEl: '.homeCardPrev',
      },
      watchSlidesProgress: true,
    });
  }

  private initIndustrySection(): void {
    if (typeof Swiper === 'undefined' || typeof gsap === 'undefined') return;

    const parent = document.querySelector('.industry-swiper') as HTMLElement;
    const child = document.querySelector('.industryHover') as HTMLElement;
    if (!parent || !child) return;

    let targetX2 = 0,
      targetY2 = 0,
      currentX2 = 0,
      currentY2 = 0;

    function animate2() {
      currentX2 += (targetX2 - currentX2) * 0.1;
      currentY2 += (targetY2 - currentY2) * 0.1;
      child.style.transform = `translate(calc(${currentX2}px), calc(${currentY2}px))`;
      requestAnimationFrame(animate2);
    }
    animate2();

    parent.addEventListener('mousemove', (e: MouseEvent) => {
      const rect = parent.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      targetX2 = (x / rect.width - 0.5) * 420;
      targetY2 = (y / rect.height - 0.5) * 420;
    });

    const industrySwiper = new Swiper('.industrySwiper', {
      slidesPerView: 1,
      speed: 800,
      spaceBetween: 40,
      effect: 'fade',
      fadeEffect: { crossfade: true },
    });

    const vidInd = document.querySelectorAll('.industry-media video') as NodeListOf<HTMLVideoElement>;
    if (vidInd.length > 0) {
      this.tryPlayVideo(vidInd[0]);
    }

    $('.industry-acc').click(function (this: any) {
      $('.industry-acc').removeClass('active');
      $(this).addClass('active');
      $(this).one('transitionend', function (this: any) {
        const indTop = $(this).position().top;
        gsap.to('.industry-acc-bg', { top: indTop, duration: 0.3 });
      });
      industrySwiper.slideTo($(this).index() - 1);
    });

    industrySwiper.on('slideChange', () => {
      $('.industry-acc').removeClass('active');
      $('.industry-acc').eq(industrySwiper.activeIndex).addClass('active');
      this.tryPlayVideo(vidInd[industrySwiper.activeIndex]);
      $('.industry-acc')
        .eq(industrySwiper.activeIndex)
        .one('transitionend', function () {
          const indTop = $('.industry-acc').eq(industrySwiper.activeIndex).position()?.top;
          gsap.to('.industry-acc-bg', { top: indTop, duration: 0.3 });
        });
    });
  }

  private tryPlayVideo(video?: HTMLVideoElement | null): void {
    if (!video) return;

    video.muted = true;
    video.playsInline = true;

    const playPromise = video.play();
    if (playPromise && typeof playPromise.catch === 'function') {
      playPromise.catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'NotAllowedError') {
          return;
        }

        // eslint-disable-next-line no-console
        console.warn('Industry video playback failed:', error);
      });
    }
  }

  private initLottieAnimations(): void {
    if (typeof lottie === 'undefined') return;

    document.querySelectorAll('.hover-card').forEach((parentEl: any) => {
      const container = parentEl.querySelector('.lottie');
      if (!container) return;

      const anim = lottie.loadAnimation({
        container: container,
        renderer: 'canvas',
        loop: container.dataset.animLoop === 'true',
        autoplay: false,
        path: container.dataset.path,
      });

      parentEl.addEventListener('mouseenter', () => anim.play());
      parentEl.addEventListener('mouseleave', () => anim.stop());
    });
  }

  /** Fade-in animations from common.js using IntersectionObserver */
  private initFadeAnimations(): void {
    const animationMap: Record<string, string> = {
      blur: 'text-focus-in',
      'fade-up': 'fade-in-bottom',
    };

    this.animObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const animType = entry.target.getAttribute('data-anim');
            if (animType) {
              const animClass = animationMap[animType];
              if (animClass) {
                entry.target.classList.add(animClass);
                this.animObserver?.unobserve(entry.target);
              }
            }
          }
        });
      },
      { threshold: 0 }
    );

    document.querySelectorAll('[data-anim]').forEach((el) => this.animObserver!.observe(el));
  }

  /** Accordion functionality from common.js */
  private initAccordions(): void {
    // Initial state: first panel open
    $('.acc-item').find('.panel').slideUp();
    $('.acc-item').eq(0).find('.panel').slideDown();
    $('.acc-container').each((_i: number, e: any) => $(e).find('.panel:first').slideDown());

    $(document).on('click.homeAccordion', '.acc-item', function (this: any) {
      const accContainer = $(this).closest('.acc-container');
      accContainer.find('.acc-item').not(this).removeClass('active').find('.panel').slideUp();
      $(this).toggleClass('active').find('.panel').slideToggle();
    });

    // Prevent rapid clicks
    $(document).on('click.homeAccordionThrottle', '.acc-item.active', function (this: any) {
      $(this).css('pointer-events', 'none');
      setTimeout(() => {
        $('.acc-item').css('pointer-events', 'all');
      }, 1000);
    });
  }
}
