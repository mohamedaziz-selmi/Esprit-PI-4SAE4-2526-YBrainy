import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, HostListener, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PackService } from '../../services/pack.service';
import { CategoryService } from '../../services/category.service';
import { FinanceService, ScraperRunStatus } from '../../services/finance.service';
import { RecommendationService } from '../../services/recommendation.service';
import { PackConversionService } from '../../services/pack-conversion.service';
import { PackPricingService } from '../../services/pack-pricing.service';
import { Pack, PackLevel, PackStatus, PageResponse } from '../../models/pack.model';
import { PackCategory } from '../../models/pack-category.model';
import { RecommendationSummary } from '../../models/recommendation.model';
import { PackConversionScore, PackConversionSummary } from '../../models/pack-conversion.model';
import { PackPricingRecommendation, PackPricingSummary } from '../../models/pack-pricing.model';
import { RuntimePageStyleService } from '../runtime-page-style.service';

@Component({
  selector: 'app-pack-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './pack-list.component.html',
  styleUrl: './pack-list.component.css',
  encapsulation: ViewEncapsulation.None,
  host: { style: 'display:block' }
})
export class PackListComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly detachPageStyles: () => void;

  packs: Pack[] = [];
  filteredPacks: Pack[] = [];
  categories: PackCategory[] = [];
  loading = false;
  error = '';

  // Dashboard Stats
  totalPacks = 0;
  totalCoursesBundled = 0;
  activePacks = 0;
  totalRevenue = 0;

  // Search & Filters
  searchTerm = '';

  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;

  // Filters
  filterCategoryId: number | undefined;
  filterLevel: PackLevel | undefined;
  filterStatus: PackStatus | undefined;

  // Scraper launcher
  scraperStarting = false;
  scraperNoticeType: 'success' | 'error' | null = null;
  scraperNoticeMessage = '';

  // Status dropdown (Angular-controlled to avoid Bootstrap conflicts)
  openStatusDropdownId: number | null = null;

  // Form & Modals
  form!: FormGroup;
  isEdit = false;
  editingPackId: number | null = null;
  submitting = false;
  selectedFile: File | null = null;
  formError = '';
  isDeleteModalOpen = false;
  deletingPack = false;
  deleteError = '';
  packToDelete: Pack | null = null;

  // Recommendations modal
  recommendationsModalOpen = false;
  recommendationsLoading = false;
  recommendationsError = '';
  recommendationsLimit = 10;
  recommendationsSummary: RecommendationSummary | null = null;

  // Conversion score modal
  conversionModalOpen = false;
  conversionLoading = false;
  conversionError = '';
  conversionLimit = 10;
  conversionSummary: PackConversionSummary | null = null;

  // Dynamic pricing modal
  pricingModalOpen = false;
  pricingLoading = false;
  pricingError = '';
  pricingLimit = 10;
  pricingSummary: PackPricingSummary | null = null;

  // AI content generation
  contentGenerating = false;
  contentGenerationError = '';
  contentGenerationSuccess = '';

  constructor(
    private fb: FormBuilder,
    private packService: PackService,
    private categoryService: CategoryService,
    private financeService: FinanceService,
    private recommendationService: RecommendationService,
    private packConversionService: PackConversionService,
    private packPricingService: PackPricingService,
    private pageStyles: RuntimePageStyleService
  ) {
    this.detachPageStyles = this.pageStyles.attach(['assets/backoffice/pages/pack-list-page.css']);
  }

  ngOnInit(): void {
    this.initForm();
    this.loadCategories();
    this.loadPacks();
  }

  private initForm(): void {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
      description: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
      originalPrice: [null, [Validators.required, Validators.min(0.01), Validators.max(999999.99)]],
      salePrice: [null, [Validators.required, Validators.min(0), Validators.max(999999.99)]],
      level: ['', [Validators.required]],
      durationHours: [null, [Validators.required, Validators.min(1), Validators.max(9999)]],
      certificateName: ['', [Validators.maxLength(100)]],
      categoryId: [null, [Validators.required]]
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.loadScripts();
    }, 100);
  }

  ngOnDestroy(): void {
    this.detachPageStyles();
  }

  private loadScripts(): void {
    const scripts = [
      'assets/backoffice/vendor/global/global.min.js',
      'assets/backoffice/vendor/chart.js/Chart.bundle.min.js',
      'assets/backoffice/vendor/apexchart/apexchart.js',
      'assets/backoffice/js/custom.js',
      'assets/backoffice/js/dlabnav-init.js',
      'assets/backoffice/js/demo.js'
    ];
    this.loadScriptSequence(scripts);
  }

  private loadScriptSequence(scripts: string[], index = 0): void {
    if (index >= scripts.length) return;
    const script = document.createElement('script');
    script.src = scripts[index];
    script.async = false;
    script.onload = () => {
      if (scripts[index].includes('global.min.js')) {
        this.mockLegacyPlugins();
      }
      this.loadScriptSequence(scripts, index + 1);
    };
    script.onerror = (err) => console.error(`Error loading script ${scripts[index]}`, err);
    document.body.appendChild(script);
  }

  private mockLegacyPlugins(): void {
    const win = window as any;
    if (win.jQuery && win.jQuery.fn) {
      if (!win.jQuery.fn.selectpicker) win.jQuery.fn.selectpicker = function () { return this; };
      if (!win.jQuery.fn.niceSelect) win.jQuery.fn.niceSelect = function () { return this; };
    }
  }

  loadCategories(): void {
    this.categoryService.getAll().subscribe({
      next: (data) => this.categories = data,
      error: (err) => console.error('Failed to load categories', err)
    });
  }

  loadPacks(): void {
    this.loading = true;
    this.error = '';
    this.packService.getAll({
      page: this.currentPage,
      size: this.pageSize,
      categoryId: this.filterCategoryId,
      level: this.filterLevel,
      status: this.filterStatus
    }).subscribe({
      next: (page: PageResponse<Pack>) => {
        this.packs = page.content;
        this.totalPages = page.totalPages;
        this.totalElements = page.totalElements;
        this.applySearchFilter();
        this.calculateStats();
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load packs';
        this.loading = false;
        console.error(err);
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadPacks();
  }

  onSearchChange(): void {
    this.applySearchFilter();
  }

  private applySearchFilter(): void {
    if (!this.searchTerm) {
      this.filteredPacks = this.packs;
    } else {
      const term = this.searchTerm.toLowerCase();
      this.filteredPacks = this.packs.filter(p =>
        p.title.toLowerCase().includes(term) ||
        (p.description && p.description.toLowerCase().includes(term)) ||
        p.categoryName?.toLowerCase().includes(term)
      );
    }
  }

  private calculateStats(): void {
    // Note: These stats are usually better fetched from a dedicated API endpoint 
    // for accuracy across all pages, but for now we calculate what we have.
    this.totalPacks = this.totalElements;
    this.activePacks = this.packs.filter(p => p.status === 'ACTIVE').length; // Fallback
    this.totalRevenue = this.packs.reduce((sum, p) => sum + (p.salePrice || 0), 0); // Simplified
    this.totalCoursesBundled = this.packs.reduce((sum, p) => sum + (p.durationHours || 0), 0) / 10; // Mock logic
  }

  clearFilters(): void {
    this.filterCategoryId = undefined;
    this.filterLevel = undefined;
    this.filterStatus = undefined;
    this.searchTerm = '';
    this.currentPage = 0;
    this.loadPacks();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadPacks();
  }

  runScraper(): void {
    if (this.scraperStarting) return;

    this.scraperStarting = true;
    this.scraperNoticeType = null;
    this.scraperNoticeMessage = '';

    this.financeService.runScraper().subscribe({
      next: (status: ScraperRunStatus) => {
        this.scraperStarting = false;
        if (status.state === 'already_running') {
          this.scraperNoticeType = 'error';
          this.scraperNoticeMessage = 'Scraper is already running.';
          return;
        }
        this.scraperNoticeType = 'success';
        this.scraperNoticeMessage = 'Scraper started successfully.';
      },
      error: (err) => {
        this.scraperStarting = false;
        const status = err?.error as ScraperRunStatus | undefined;
        if (status?.state === 'already_running') {
          this.scraperNoticeType = 'error';
          this.scraperNoticeMessage = 'Scraper is already running.';
          return;
        }
        this.scraperNoticeType = 'error';
        this.scraperNoticeMessage =
          err?.error?.message ||
          err?.error?.error ||
          err?.message ||
          'Could not start scraper.';
      }
    });
  }

  openRecommendationsModal(): void {
    this.recommendationsModalOpen = true;
    this.fetchRecommendations();
  }

  closeRecommendationsModal(): void {
    this.recommendationsModalOpen = false;
  }

  openConversionModal(): void {
    this.conversionModalOpen = true;
    this.fetchConversionSummary();
  }

  closeConversionModal(): void {
    this.conversionModalOpen = false;
  }

  openPricingModal(): void {
    this.pricingModalOpen = true;
    this.fetchPricingSummary();
  }

  closePricingModal(): void {
    this.pricingModalOpen = false;
  }

  fetchRecommendations(): void {
    this.recommendationsLoading = true;
    this.recommendationsError = '';

    this.recommendationService.getSummary(this.recommendationsLimit).subscribe({
      next: (summary) => {
        this.recommendationsSummary = summary;
        this.recommendationsLoading = false;
      },
      error: (err) => {
        this.recommendationsLoading = false;
        this.recommendationsSummary = null;
        this.recommendationsError = err?.error?.message || err?.message || 'Failed to load recommendations.';
      }
    });
  }

  fetchConversionSummary(): void {
    this.conversionLoading = true;
    this.conversionError = '';

    this.packConversionService.getSummary(this.conversionLimit).subscribe({
      next: (summary) => {
        this.conversionSummary = summary;
        this.conversionLoading = false;
      },
      error: (err) => {
        this.conversionLoading = false;
        this.conversionSummary = null;
        this.conversionError = err?.error?.message || err?.message || 'Failed to load conversion insights.';
      }
    });
  }

  fetchPricingSummary(): void {
    this.pricingLoading = true;
    this.pricingError = '';

    this.packPricingService.getSummary(this.pricingLimit).subscribe({
      next: (summary) => {
        this.pricingSummary = summary;
        this.pricingLoading = false;
      },
      error: (err) => {
        this.pricingLoading = false;
        this.pricingSummary = null;
        this.pricingError = err?.error?.message || err?.message || 'Failed to load dynamic pricing insights.';
      }
    });
  }

  generatePackContent(): void {
    if (this.contentGenerating) {
      return;
    }

    this.contentGenerationError = '';
    this.contentGenerationSuccess = '';

    const title = String(this.form.get('title')?.value ?? '').trim();
    const description = String(this.form.get('description')?.value ?? '').trim();
    const level = this.form.get('level')?.value as PackLevel | '' | null;
    const durationRaw = this.form.get('durationHours')?.value;
    const durationHours = Number(durationRaw);
    const certificateName = String(this.form.get('certificateName')?.value ?? '').trim();
    const categoryId = Number(this.form.get('categoryId')?.value);
    const categoryName = this.categories.find(c => c.id === categoryId)?.name ?? '';

    if (!title && !description && !categoryName) {
      this.contentGenerationError = 'Please enter at least a title or category before generating.';
      return;
    }

    this.contentGenerating = true;

    this.packService.generateContent({
      title: title || undefined,
      description: description || undefined,
      categoryName: categoryName || undefined,
      level: level || undefined,
      durationHours: Number.isFinite(durationHours) && durationHours > 0 ? durationHours : undefined,
      certificateName: certificateName || undefined
    }).subscribe({
      next: (result) => {
        const patch: Record<string, unknown> = {
          description: result.generatedDescription
        };

        if (!title && result.generatedTitle) {
          patch['title'] = result.generatedTitle;
        }

        this.form.patchValue(patch);
        this.form.get('description')?.markAsTouched();
        this.form.get('description')?.updateValueAndValidity();

        this.contentGenerationSuccess = 'AI content generated. You can edit it before saving.';
        this.contentGenerating = false;
      },
      error: (err) => {
        this.contentGenerationError = err?.error?.message || err?.message || 'Failed to generate content.';
        this.contentGenerating = false;
      }
    });
  }

  // --- Modal CRUD Operations ---

  openCreateModal(): void {
    this.isEdit = false;
    this.editingPackId = null;
    this.formError = '';
    this.selectedFile = null;
    this.resetContentGenerationState();
    this.form.reset({
      level: '',
      categoryId: null
    });
    // Trigger modal via Bootstrap JS if needed, but we use data-bs-toggle in HTML
  }

  openEditModal(pack: Pack): void {
    this.isEdit = true;
    this.editingPackId = pack.id;
    this.formError = '';
    this.selectedFile = null;
    this.resetContentGenerationState();
    this.form.patchValue({
      title: pack.title,
      description: pack.description,
      originalPrice: pack.originalPrice,
      salePrice: pack.salePrice,
      level: pack.level,
      durationHours: pack.durationHours,
      certificateName: pack.certificateName || '',
      categoryId: pack.categoryId
    });
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { originalPrice, salePrice } = this.form.value;
    if (salePrice > originalPrice) {
      this.formError = 'Sale price cannot be greater than original price';
      return;
    }

    this.submitting = true;
    this.formError = '';

    const data = { ...this.form.value };
    if (!data.certificateName) delete data.certificateName;

    const request$ = this.isEdit && this.editingPackId
      ? this.packService.update(this.editingPackId, data, this.selectedFile || undefined)
      : this.packService.create(data, this.selectedFile || undefined);

    request$.subscribe({
      next: () => {
        this.submitting = false;
        this.closeModal();
        this.loadPacks(); // Refresh list
      },
      error: (err) => {
        this.formError = err.error?.message || 'Failed to save pack';
        this.submitting = false;
        console.error(err);
      }
    });
  }

  private closeModal(): void {
    this.resetContentGenerationState();
    const modalElement = document.getElementById('packModal');
    if (modalElement) {
      const win = window as any;
      if (win.bootstrap && win.bootstrap.Modal) {
        const modalInstance = win.bootstrap.Modal.getInstance(modalElement);
        if (modalInstance) {
          modalInstance.hide();
        }
      }
    }

    // Comprehensive fallback for stuck backdrops
    setTimeout(() => {
      const backdrops = document.querySelectorAll('.modal-backdrop');
      backdrops.forEach(b => b.remove());
      document.body.classList.remove('modal-open');
      document.body.style.overflow = '';
      document.body.style.paddingRight = '';
    }, 500);
  }

  private resetContentGenerationState(): void {
    this.contentGenerating = false;
    this.contentGenerationError = '';
    this.contentGenerationSuccess = '';
  }

  toggleStatusDropdown(packId: number): void {
    this.openStatusDropdownId = this.openStatusDropdownId === packId ? null : packId;
  }

  closeStatusDropdown(): void {
    this.openStatusDropdownId = null;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.pack-status-dropdown')) {
      this.closeStatusDropdown();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.pricingModalOpen) {
      this.closePricingModal();
      return;
    }
    if (this.conversionModalOpen) {
      this.closeConversionModal();
      return;
    }
    if (this.isDeleteModalOpen) {
      this.closeDeleteModal();
      return;
    }
    this.closeStatusDropdown();
  }

  changeStatus(pack: Pack, status: PackStatus): void {
    this.closeStatusDropdown();
    this.packService.changeStatus(pack.id, status).subscribe({
      next: (updated) => {
        const idx = this.packs.findIndex(p => p.id === updated.id);
        if (idx >= 0) this.packs[idx] = updated;
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to change status');
      }
    });
  }

  deletePack(pack: Pack): void {
    this.packToDelete = pack;
    this.isDeleteModalOpen = true;
    this.deletingPack = false;
    this.deleteError = '';
  }

  closeDeleteModal(): void {
    if (this.deletingPack) return;
    this.isDeleteModalOpen = false;
    this.deleteError = '';
    this.packToDelete = null;
  }

  confirmDeletePack(): void {
    if (!this.packToDelete || this.deletingPack) return;

    this.deletingPack = true;
    this.deleteError = '';

    this.packService.delete(this.packToDelete.id).subscribe({
      next: () => {
        this.deletingPack = false;
        this.isDeleteModalOpen = false;
        this.packToDelete = null;
        this.loadPacks();
      },
      error: (err) => {
        this.deletingPack = false;
        this.deleteError = err.error?.message || 'Failed to delete pack';
      }
    });
  }

  getStatusClass(status: PackStatus): string {
    switch (status) {
      case 'ACTIVE': return 'bg-success';
      case 'DRAFT': return 'bg-warning text-dark';
      case 'ARCHIVED': return 'bg-secondary';
      default: return 'bg-light';
    }
  }

  getLevelClass(level: PackLevel): string {
    switch (level) {
      case 'BEGINNER': return 'bg-info text-dark';
      case 'INTERMEDIATE': return 'bg-primary';
      case 'ADVANCED': return 'bg-danger';
      default: return 'bg-light';
    }
  }

  getDiscount(pack: { originalPrice?: number | null; salePrice?: number | null } | null | undefined): number {
    const originalPrice = Number(pack?.originalPrice ?? 0);
    const salePrice = Number(pack?.salePrice ?? 0);
    if (!originalPrice) return 0;
    return Math.round(((originalPrice - salePrice) / originalPrice) * 100);
  }

  getConfidenceBadgeClass(level: string | null | undefined): string {
    const normalized = (level || '').toUpperCase();
    if (normalized === 'HIGH') return 'bg-success';
    if (normalized === 'MEDIUM') return 'bg-warning text-dark';
    return 'bg-secondary';
  }

  getImpactBadgeClass(level: string | null | undefined): string {
    const normalized = (level || '').toUpperCase();
    if (normalized === 'IMMEDIATE') return 'bg-danger';
    if (normalized === 'STRONG') return 'bg-primary';
    return 'bg-secondary';
  }

  getPriorityBadgeClass(level: string | null | undefined): string {
    const normalized = (level || '').toUpperCase();
    if (normalized === 'HIGH') return 'bg-danger';
    if (normalized === 'MEDIUM') return 'bg-warning text-dark';
    return 'bg-secondary';
  }

  getConversionRatePercent(rate: number | null | undefined): number {
    return Math.round((Number(rate || 0) * 100) * 100) / 100;
  }

  getConversionRateWidth(rate: number | null | undefined): number {
    const value = this.getConversionRatePercent(rate);
    return Math.max(4, Math.min(value, 100));
  }

  getConversionBadgeClass(rate: number | null | undefined): string {
    const value = Number(rate || 0);
    if (value >= 0.6) return 'pack-conversion-pill--hot';
    if (value >= 0.45) return 'pack-conversion-pill--warm';
    if (value >= 0.3) return 'pack-conversion-pill--steady';
    return 'pack-conversion-pill--watch';
  }

  getConversionMood(rate: number | null | undefined): string {
    const value = Number(rate || 0);
    if (value >= 0.6) return 'Hot Pick';
    if (value >= 0.45) return 'Strong Match';
    if (value >= 0.3) return 'Promising';
    return 'Watch List';
  }

  getConversionBarClass(index: number): string {
    if (index === 0) return 'pack-conversion-bar--gold';
    if (index === 1) return 'pack-conversion-bar--teal';
    if (index === 2) return 'pack-conversion-bar--violet';
    return 'pack-conversion-bar--neutral';
  }

  get topConversionPacks(): PackConversionScore[] {
    return this.conversionSummary?.topConversionPacks || [];
  }

  get conversionPodium(): PackConversionScore[] {
    return this.topConversionPacks.slice(0, 3);
  }

  getPricingConfidencePercent(confidence: number | null | undefined): number {
    return Math.round((Number(confidence || 0) * 100) * 100) / 100;
  }

  getPricingActionClass(action: string | null | undefined): string {
    const normalized = (action || '').toLowerCase();
    if (normalized === 'increase') return 'pack-pricing-action--increase';
    if (normalized === 'decrease') return 'pack-pricing-action--decrease';
    return 'pack-pricing-action--hold';
  }

  getPricingActionLabel(action: string | null | undefined): string {
    const normalized = (action || '').toLowerCase();
    if (normalized === 'increase') return 'Raise Price';
    if (normalized === 'decrease') return 'Lower Price';
    return 'Hold Price';
  }

  getPricingActionIcon(action: string | null | undefined): string {
    const normalized = (action || '').toLowerCase();
    if (normalized === 'increase') return 'trending_up';
    if (normalized === 'decrease') return 'trending_down';
    return 'horizontal_rule';
  }

  getPricingLiftClass(value: number | null | undefined): string {
    return Number(value || 0) >= 0 ? 'pack-pricing-lift--positive' : 'pack-pricing-lift--negative';
  }

  getPricingConfidenceClass(confidence: number | null | undefined): string {
    const value = Number(confidence || 0);
    if (value >= 0.72) return 'pack-pricing-confidence--high';
    if (value >= 0.6) return 'pack-pricing-confidence--medium';
    return 'pack-pricing-confidence--low';
  }

  getPricingBandClass(band: string | null | undefined): string {
    const normalized = (band || '').toLowerCase();
    if (normalized.includes('margin')) return 'pack-pricing-band--margin';
    if (normalized.includes('balanced')) return 'pack-pricing-band--balanced';
    if (normalized.includes('conversion')) return 'pack-pricing-band--conversion';
    return 'pack-pricing-band--aggressive';
  }

  getPriceDelta(currentPrice: number | null | undefined, recommendedPrice: number | null | undefined): number {
    return Math.round((Number(recommendedPrice || 0) - Number(currentPrice || 0)) * 100) / 100;
  }

  getDiscountRangeLabel(minPct: number | null | undefined, maxPct: number | null | undefined): string {
    const minValue = Number(minPct || 0);
    const maxValue = Number(maxPct || 0);
    if (Math.abs(minValue - maxValue) < 0.01) {
      return `${minValue.toFixed(1)}%`;
    }
    return `${minValue.toFixed(1)}% - ${maxValue.toFixed(1)}%`;
  }

  get topPricingRecommendations(): PackPricingRecommendation[] {
    return this.pricingSummary?.topPricingRecommendations || [];
  }

  get pricingSpotlight(): PackPricingRecommendation[] {
    return this.topPricingRecommendations.slice(0, 3);
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }
}

