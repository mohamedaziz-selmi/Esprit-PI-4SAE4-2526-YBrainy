import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Subject, takeUntil } from 'rxjs';
import { AiSearchResult, AiCourseItem, Course, MlRecommendation, MlRecommendationsResponse, MlQualityResult } from '../../models/course.models';
import { CourseApiService, SpringPage } from '../../services/course-api.service';
import { UserSessionService } from '../../../tracking/user-session.service';
import { courseMediaUrl } from '../../utils/course-media-url';

@Component({
  selector: 'app-courses',
  standalone: false,
  templateUrl: './courses.component.html',
  styleUrls: ['./courses.component.css'],
  host: { 'style': 'display:block' }
})
export class CoursesComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  private readonly _courses$ = new BehaviorSubject<Course[]>([]);
  courses$ = this._courses$.asObservable();

  @ViewChild('catCarousel') catCarouselRef!: ElementRef<HTMLElement>;
  private rawCourses: Course[] = [];
  carouselPaused = false;
  private autoScrollInterval: ReturnType<typeof setInterval> | null = null;
  filtersExpanded = false;

  loading = false;

  page = 0;
  size = 12;
  totalPages = 1;
  totalElements = 0;

  search = '';
  category = '';
  level = '';
  published: '' | 'true' | 'false' = '';

  sortField = 'RATING';
  sortDir: 'ASC' | 'DESC' = 'DESC';

  selectedRating = 0;
  selectedPriceChip = '';
  priceMin = 0;
  priceMax = 200;
  selectedDuration = '';

  gridView = true;

  aiSearchQuery: string = '';
  aiSearchMode: boolean = false;
  aiSearchResult: AiSearchResult | null = null;
  aiSearchLoading: boolean = false;
  aiSearchError: string = '';

  readonly categories = [
    { label: 'All',          value: '' },
    { label: 'Programming',  value: 'PROGRAMMING' },
    { label: 'Design',       value: 'DESIGN' },
    { label: 'Business',     value: 'BUSINESS' },
    { label: 'Science',      value: 'SCIENCE' },
    { label: 'Music',        value: 'MUSIC' },
    { label: 'Language',     value: 'LANGUAGE' },
    { label: 'Math',         value: 'MATH' },
    { label: 'Photography',  value: 'PHOTOGRAPHY' },
    { label: 'Marketing',    value: 'MARKETING' },
    { label: 'Other',        value: 'OTHER' },
  ];

  readonly levels = [
    { label: 'All',          value: '' },
    { label: 'Beginner',     value: 'BEGINNER' },
    { label: 'Intermediate', value: 'INTERMEDIATE' },
    { label: 'Advanced',     value: 'ADVANCED' },
  ];

  readonly sortChips = [
    { label: 'Rating', field: 'RATING' },
    { label: 'Price',  field: 'PRICE'  },
    { label: 'Newest', field: 'NEWEST' },
    { label: 'Title',  field: 'TITLE'  },
  ];

  readonly ratingFilters = [
    { label: 'All',      min: 0   },
    { label: '⭐ 5.0',   min: 5   },
    { label: '⭐ 4.5+',  min: 4.5 },
    { label: '⭐ 4.0+',  min: 4   },
    { label: '⭐ 3.0+',  min: 3   },
  ];

  readonly priceChips = [
    { label: 'All',        key: ''       },
    { label: 'FREE',       key: 'free'   },
    { label: '$1 – 50',    key: '1-50'   },
    { label: '$51 – 100',  key: '51-100' },
    { label: '$100+',      key: '100+'   },
  ];

  readonly durationChips = [
    { label: 'All',        value: ''        },
    { label: '< 1hr',      value: '<60'     },
    { label: '1 – 3hrs',   value: '60-180'  },
    { label: '3 – 10hrs',  value: '180-600' },
    { label: '10hrs+',     value: '600+'    },
  ];

  // ── ML Recommendations ────────────────────────────────────────────
  mlRecommendations: MlRecommendation[] = [];
  mlRecommendationsLoading: boolean = false;
  enrolledCourseIds: Set<number> = new Set();
  mlBasedOn: { category: string; level: string } | null = null;
  mlRecommendationsVisible: boolean = false;
  hoveredRecommendation: number | null = null;
  mlOverlayOpen: boolean = false;
  mlOverlayClosing: boolean = false;
  courseQualityMap: {[key: number]: MlQualityResult} = {};
  qualityHovered: number | null = null;

  constructor(
    private api: CourseApiService,
    private router: Router,
    private userSession: UserSessionService
  ) {}

  ngOnInit(): void {
    this.loadPage();
    this.loadMLRecommendations();
  }

  async ngAfterViewInit(): Promise<void> {
    this.startAutoScroll();
    this.attachWheelListener();
  }

  private startAutoScroll(): void {
    this.autoScrollInterval = setInterval(() => {
      if (this.carouselPaused) return;
      const el = this.catCarouselRef?.nativeElement;
      if (!el) return;
      
      // Check if we've scrolled to the end, reset to start for infinite loop effect
      if (el.scrollLeft >= el.scrollWidth - el.clientWidth - 1) {
        el.scrollLeft = 0;
      } else {
        el.scrollLeft += 1;
      }
    }, 20);
  }

  private attachWheelListener(): void {
    setTimeout(() => {
      const el = this.catCarouselRef?.nativeElement;
      if (!el) return;
      el.addEventListener('wheel', (event: WheelEvent) => {
        event.preventDefault();
        el.scrollLeft += event.deltaY * 0.8;
      }, { passive: false });
    }, 500);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.autoScrollInterval) {
      clearInterval(this.autoScrollInterval);
    }
  }

  goToDetail(course: Course): void {
    this.router.navigate(['/courses', course.id]);
  }

  // ── Carousel ─────────────────────────────────────────────────────

  scrollCarousel(direction: -1 | 1): void {
    if (!this.catCarouselRef) return;
    const el = this.catCarouselRef.nativeElement;
    el.scrollTo({ left: el.scrollLeft + direction * 150, behavior: 'smooth' });
  }

  pauseCarousel(): void  { this.carouselPaused = true;  }
  resumeCarousel(): void { this.carouselPaused = false; }

  // ── Sort chips ────────────────────────────────────────────────────

  toggleSort(field: string): void {
    if (this.sortField === field) {
      this.sortDir = this.sortDir === 'DESC' ? 'ASC' : 'DESC';
    } else {
      this.sortField = field;
      this.sortDir = 'DESC';
    }
    this.applyClientFilters();
  }

  // ── Server-side filter handlers ───────────────────────────────────

  selectCategory(value: string): void {
    this.category = value;
    this.page = 0;
    this.loadPage();
  }

  selectLevel(value: string): void {
    this.level = value;
    this.page = 0;
    this.loadPage();
  }

  onSearchChange(value: string): void {
    this.search = value;
    this.page = 0;
    this.loadPage();
  }

  // ── Client-side filter handlers ───────────────────────────────────

  applyRatingFilter(min: number): void {
    this.selectedRating = min;
    this.applyClientFilters();
  }

  applyPriceFilter(key: string): void {
    this.selectedPriceChip = key;
    switch (key) {
      case 'free':    this.priceMin = 0;   this.priceMax = 0;   break;
      case '1-50':    this.priceMin = 1;   this.priceMax = 50;  break;
      case '51-100':  this.priceMin = 51;  this.priceMax = 100; break;
      case '100+':    this.priceMin = 100; this.priceMax = 200; break;
      default:        this.priceMin = 0;   this.priceMax = 200; break;
    }
    this.applyClientFilters();
  }

  onPriceMinChange(val: number): void {
    this.priceMin = Math.min(val, this.priceMax - 5);
    this.selectedPriceChip = 'custom';
    this.applyClientFilters();
  }

  onPriceMaxChange(val: number): void {
    this.priceMax = Math.max(val, this.priceMin + 5);
    this.selectedPriceChip = 'custom';
    this.applyClientFilters();
  }

  selectDuration(value: string): void {
    this.selectedDuration = value;
    this.applyClientFilters();
  }

  // ── Active filters ────────────────────────────────────────────────

  get activeFilters(): Array<{ key: string; label: string }> {
    const out: Array<{ key: string; label: string }> = [];
    if (this.category) {
      const c = this.categories.find(x => x.value === this.category);
      out.push({ key: 'category', label: c?.label ?? this.category });
    }
    if (this.level) {
      const l = this.levels.find(x => x.value === this.level);
      out.push({ key: 'level', label: l?.label ?? this.level });
    }
    if (this.search) {
      out.push({ key: 'search', label: `"${this.search}"` });
    }
    if (this.selectedRating) {
      const r = this.ratingFilters.find(x => x.min === this.selectedRating);
      out.push({ key: 'rating', label: r?.label ?? `⭐ ${this.selectedRating}+` });
    }
    if (this.selectedPriceChip && this.selectedPriceChip !== 'custom') {
      const p = this.priceChips.find(x => x.key === this.selectedPriceChip);
      out.push({ key: 'price', label: p?.label ?? this.selectedPriceChip });
    } else if (this.selectedPriceChip === 'custom') {
      out.push({ key: 'price', label: `$${this.priceMin} – $${this.priceMax}` });
    }
    if (this.selectedDuration) {
      const d = this.durationChips.find(x => x.value === this.selectedDuration);
      out.push({ key: 'duration', label: d?.label ?? this.selectedDuration });
    }
    return out;
  }

  removeFilter(key: string): void {
    switch (key) {
      case 'category': this.selectCategory('');     break;
      case 'level':    this.selectLevel('');        break;
      case 'search':   this.onSearchChange('');     break;
      case 'rating':   this.applyRatingFilter(0);   break;
      case 'price':    this.applyPriceFilter('');   break;
      case 'duration': this.selectDuration('');     break;
    }
  }

  clearAllFilters(): void {
    this.category = '';
    this.level = '';
    this.search = '';
    this.selectedRating = 0;
    this.selectedPriceChip = '';
    this.priceMin = 0;
    this.priceMax = 200;
    this.selectedDuration = '';
    this.page = 0;
    this.aiSearchMode = false;
    this.aiSearchQuery = '';
    this.aiSearchResult = null;
    this.aiSearchError = '';
    this.loadPage();
  }

  // ── Pagination ────────────────────────────────────────────────────

  get pageNumbers(): number[] {
    const total = this.totalPages;
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    const start = Math.max(0, Math.min(this.page - 3, total - 7));
    return Array.from({ length: 7 }, (_, i) => start + i);
  }

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages) return;
    this.page = p;
    this.loadPage();
  }

  prevPage(): void {
    if (this.page <= 0) return;
    this.page -= 1;
    this.loadPage();
  }

  nextPage(): void {
    if (this.page >= this.totalPages - 1) return;
    this.page += 1;
    this.loadPage();
  }

  // ── Display helpers ───────────────────────────────────────────────

  ratingStars(rating: number): string {
    const r = Math.round(Number(rating) || 0);
    return '★'.repeat(Math.min(Math.max(r, 0), 5)) + '☆'.repeat(Math.max(0, 5 - r));
  }

  formatPrice(price: number): string {
    const n = Number(price);
    if (!isFinite(n)) return '--';
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(n);
    } catch {
      return `$${n.toFixed(2)}`;
    }
  }

  levelClass(level: string): string {
    return 'cl-level-' + String(level || '').toLowerCase().replace('_', '-');
  }

  formatDuration(minutes: number): string {
    const m = Number(minutes) || 0;
    if (m <= 0) return '';
    if (m < 60) return `${m}m`;
    const h = Math.floor(m / 60);
    const r = m % 60;
    return r ? `${h}h ${r}m` : `${h}h`;
  }

  levelLabel(level: string): string {
    if (!level) return '';
    const s = String(level).toLowerCase().replace(/_/g, ' ');
    return s.charAt(0).toUpperCase() + s.slice(1);
  }

  // ── AI Search ─────────────────────────────────────────────────────

  performAiSearch(): void {
    if (!this.aiSearchQuery.trim()) return;
    this.aiSearchLoading = true;
    this.aiSearchMode = true;
    this.aiSearchError = '';
    this.aiSearchResult = null;
    this.api.aiSearch(this.aiSearchQuery).subscribe({
      next: (result) => {
        this.aiSearchResult = result;
        this.aiSearchLoading = false;
      },
      error: () => {
        this.aiSearchError = 'AI search failed. Try again.';
        this.aiSearchLoading = false;
      }
    });
  }

  clearAiSearch(): void {
    this.aiSearchMode = false;
    this.aiSearchQuery = '';
    this.aiSearchResult = null;
    this.aiSearchError = '';
  }

  normalizeThumbnail(url: string): string {
    return courseMediaUrl(url);
  }

  // ── Legacy handlers (kept for compatibility) ───────────────────────

  onCategoryChange(value: string): void { this.selectCategory(value); }
  onLevelChange(value: string): void    { this.selectLevel(value); }
  onSortChange(_value: string): void    { /* replaced by toggleSort */ }

  onPublishedChange(value: '' | 'true' | 'false'): void {
    this.published = value;
    this.page = 0;
    this.loadPage();
  }

  onSizeChange(value: string): void {
    const n = parseInt(value, 10);
    this.size = !isNaN(n) && n > 0 ? n : 12;
    this.page = 0;
    this.loadPage();
  }

  // ── ML Recommendations ────────────────────────────────────────────

  loadMLRecommendations(): void {
    const studentId = (this.userSession.get()?.userId ?? 0);
    if (!studentId) {
      this.fetchRecommendations('PROGRAMMING', 'BEGINNER');
      return;
    }
    this.mlRecommendationsLoading = true;
    this.api.getStudentEnrollments(studentId).subscribe({
      next: (enrollments) => {
        this.enrolledCourseIds = new Set(
          enrollments.map((e: any) => e.courseId || e.course?.id)
            .filter((id: any) => id != null)
            .map(Number)
        );
        this.api.getRecommendationsByStudent(studentId, 5).subscribe({
          next: (response) => {
            const filtered = (response.recommendations || []).filter(
              (r: any) => !this.enrolledCourseIds.has(Number(r.courseId))
            );
            this.mlRecommendations = filtered;
            this.mlBasedOn = response.basedOn;
            this.mlRecommendationsLoading = false;
            setTimeout(() => { this.mlRecommendationsVisible = true; }, 100);
          },
          error: () => { this.mlRecommendationsLoading = false; }
        });
      },
      error: () => this.fetchRecommendations('PROGRAMMING', 'BEGINNER')
    });
  }

  fetchRecommendations(category: string, level: string): void {
    this.api.getMLRecommendations(category, level, 5).subscribe({
      next: (response) => {
        const filtered = (response.recommendations || []).filter(
          (r: any) => !this.enrolledCourseIds.has(Number(r.courseId))
        );
        this.mlRecommendations = filtered;
        this.mlBasedOn = response.basedOn;
        this.mlRecommendationsLoading = false;
        setTimeout(() => { this.mlRecommendationsVisible = true; }, 100);
      },
      error: () => { this.mlRecommendationsLoading = false; }
    });
  }

  getMatchScore(rec: MlRecommendation, index: number = 0): number {
    const ratingScore = (rec.rating / 5) * 70;
    const freeBonus = (!rec.price || rec.price === 0) ? 10 : 0;
    const positionBonus = Math.max(0, 20 - (index * 4));
    const lectureBonus = Math.min(10, Math.floor(rec.numLectures / 20));
    return Math.min(99, Math.round(ratingScore + freeBonus + positionBonus + lectureBonus));
  }

  getMatchLabel(score: number): string {
    if (score >= 90) return 'Perfect Match';
    if (score >= 75) return 'Great Match';
    if (score >= 60) return 'Good Match';
    return 'Suggested';
  }

  getMatchColor(score: number): string {
    if (score >= 90) return '#48bb78';
    if (score >= 75) return '#667eea';
    if (score >= 60) return '#fbbf24';
    return '#a0aec0';
  }

  getCategoryColor(category: string): string {
    const colors: Record<string, string> = {
      'PROGRAMMING':  'linear-gradient(135deg,#667eea,#764ba2)',
      'DESIGN':       'linear-gradient(135deg,#f093fb,#f5576c)',
      'BUSINESS':     'linear-gradient(135deg,#4facfe,#00f2fe)',
      'SCIENCE':      'linear-gradient(135deg,#43e97b,#38f9d7)',
      'MATH':         'linear-gradient(135deg,#fa709a,#fee140)',
      'LANGUAGE':     'linear-gradient(135deg,#a18cd1,#fbc2eb)',
      'MUSIC':        'linear-gradient(135deg,#ffecd2,#fcb69f)',
      'MARKETING':    'linear-gradient(135deg,#ff9a9e,#fecfef)',
      'PHOTOGRAPHY':  'linear-gradient(135deg,#a1c4fd,#c2e9fb)',
      'OTHER':        'linear-gradient(135deg,#d4fc79,#96e6a1)'
    };
    return colors[category] || colors['OTHER'];
  }

  openMLOverlay(): void {
    this.mlOverlayOpen = true;
    this.mlOverlayClosing = false;
    document.body.style.overflow = 'hidden';
  }

  closeMLOverlay(): void {
    this.mlOverlayClosing = true;
    setTimeout(() => {
      this.mlOverlayOpen = false;
      this.mlOverlayClosing = false;
      document.body.style.overflow = '';
    }, 500);
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.mlOverlayOpen) this.closeMLOverlay();
  }

  searchByRecommendation(rec: MlRecommendation): void {
    this.category = rec.category;
    this.level = rec.level || '';
    this.page = 0;
    this.loadPage();
    setTimeout(() => {
      const grid = document.querySelector('.cl-courses-grid')
        || document.querySelector('.cl-grid')
        || document.querySelector('[class*="grid"]');
      if (grid) grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 300);
  }

  // ── DSO3 Quality ──────────────────────────────────────────────────

  loadCourseQuality(courses: any[]): void {
    if (!courses || courses.length === 0) return;
    const ids = courses.map(c => c.id);
    this.api.getCourseQualityBatch(ids).subscribe({
      next: (results) => { this.courseQualityMap = results; },
      error: () => {}
    });
  }

  isHighQuality(courseId: number): boolean {
    return this.courseQualityMap[courseId]?.isHighQuality === true;
  }

  getQualityConfidence(courseId: number): number {
    return Math.round(this.courseQualityMap[courseId]?.confidencePercentage || 0);
  }

  // ── Private ───────────────────────────────────────────────────────

  private loadPage(): void {
    this.loading = true;
    // Always filter to published courses for students
    // Admins and instructors can see all courses
    const session = this.userSession.get();
    const role = session?.role || 'STUDENT';
    const publishedFilter = (role === 'ADMIN' || role === 'INSTRUCTOR')
      ? (this.published === '' ? undefined : this.published === 'true')
      : true; // Students always see only published courses
    this.api.listPage({
      page: this.page,
      size: this.size,
      search: this.search || undefined,
      category: this.category || undefined,
      level: this.level || undefined,
      isPublished: publishedFilter,
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data: SpringPage<any>) => {
          const content = Array.isArray(data?.content) ? data.content : [];
          const mapped: Course[] = content.map((c: any) => ({
            id: Number(c.id),
            title: c.title ?? '',
            description: c.description ?? '',
            thumbnailUrl: (() => {
              const raw = String(c.thumbnailUrl ?? c.thumbnailVideoPath ?? '').trim();
              return courseMediaUrl(raw);
            })(),
            teacherVoiceSamplePath: c.teacherVoiceSamplePath,
            price: c.price ?? 0,
            rating: c.rating ?? 0,
            ratingCount: c.ratingCount ?? 0,
            category: c.category ?? '',
            level: c.level ?? '',
            approximateDurationMinutes: c.approximateDurationMinutes ?? 0,
            isPublished: c.isPublished ?? false,
            offersCertificate: c.offersCertificate ?? false,
            lessonCount: c.lessonCount ?? 0,
            createdAt: c.createdAt,
            updatedAt: c.updatedAt,
          }));

          this.rawCourses = mapped;
          this.totalPages = typeof data?.totalPages === 'number' ? data.totalPages : 1;
          this.totalElements = typeof data?.totalElements === 'number' ? data.totalElements : mapped.length;
          this.applyClientFilters();
          this.loading = false;
          this.loadCourseQuality(mapped);
        },
        error: (err) => {
          console.error('Failed to load courses from the API.', err);
          this.rawCourses = [];
          this._courses$.next([]);
          this.totalPages = 1;
          this.totalElements = 0;
          this.loading = false;
        }
      });
  }

  private applyClientFilters(): void {
    const filtered = this.rawCourses.filter(c =>
      this.passesRatingFilter(c) && this.passesPriceFilter(c) && this.passesDurationFilter(c)
    );
    this._courses$.next(this.applySort(filtered));
  }

  private passesRatingFilter(course: Course): boolean {
    if (!this.selectedRating) return true;
    return (Number(course.rating) || 0) >= this.selectedRating;
  }

  private passesPriceFilter(course: Course): boolean {
    const price = Number(course.price) || 0;
    switch (this.selectedPriceChip) {
      case 'free':   return price === 0;
      case '1-50':   return price >= 1   && price <= 50;
      case '51-100': return price >= 51  && price <= 100;
      case '100+':   return price >= 100;
      case 'custom': return price >= this.priceMin && price <= this.priceMax;
      default:       return true;
    }
  }

  private passesDurationFilter(course: Course): boolean {
    if (!this.selectedDuration) return true;
    const m = Number(course.approximateDurationMinutes) || 0;
    switch (this.selectedDuration) {
      case '<60':     return m > 0 && m < 60;
      case '60-180':  return m >= 60  && m < 180;
      case '180-600': return m >= 180 && m < 600;
      case '600+':    return m >= 600;
      default:        return true;
    }
  }

  private applySort(items: Course[]): Course[] {
    const arr = (Array.isArray(items) ? items : []).slice();
    const byTitle = (a: Course, b: Course) =>
      String(a.title || '').localeCompare(String(b.title || ''), undefined, { sensitivity: 'base' });
    const byNum = (x: number, y: number, desc: boolean) => {
      if (x === y) return 0;
      return desc ? (x > y ? -1 : 1) : (x < y ? -1 : 1);
    };
    const desc = this.sortDir === 'DESC';

    switch (this.sortField) {
      case 'PRICE':
        return arr.sort((a, b) => byNum(Number(a.price) || 0, Number(b.price) || 0, desc) || byTitle(a, b));
      case 'NEWEST':
        return arr.sort((a, b) => {
          const ta = new Date(a.createdAt ?? 0).getTime();
          const tb = new Date(b.createdAt ?? 0).getTime();
          return byNum(ta, tb, desc) || byTitle(a, b);
        });
      case 'TITLE':
        return desc ? arr.sort((a, b) => -byTitle(a, b)) : arr.sort(byTitle);
      case 'RATING':
      default:
        return arr.sort((a, b) => byNum(Number(a.rating) || 0, Number(b.rating) || 0, desc) || byTitle(a, b));
    }
  }
}
