import { Component, OnDestroy, OnInit } from '@angular/core';
import { PackService } from '../../../../services/pack.service';
import { CategoryService } from '../../../../services/category.service';
import { Pack } from '../../../../models/pack.model';
import { PackCategory } from '../../../../models/pack-category.model';

@Component({
  selector: 'app-fo-pack-list',
  standalone: false,
  templateUrl: './pack-list.component.html',
  styleUrls: ['./pack-list.component.css'],
  host: { style: 'display:block' }
})
export class FoPackListComponent implements OnInit, OnDestroy {
  readonly telegramAppUrl = 'tg://';
  packs: Pack[] = [];
  filteredPacks: Pack[] = [];
  categories: PackCategory[] = [];
  selectedCategoryId: number | null = null;
  currentPage = 1;
  pageSize = 6;
  loading = true;
  error = '';
  ttsLoadingPackId: number | null = null;
  ttsPlayingPackId: number | null = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  constructor(
    private packService: PackService,
    private categoryService: CategoryService
  ) { }

  ngOnInit(): void {
    this.loadCategories();
    this.loadPacks();
  }

  loadCategories(): void {
    this.categoryService.getActiveCategories().subscribe({
      next: (data) => this.categories = data,
      error: (err) => console.error('Failed to load categories', err)
    });
  }

  loadPacks(): void {
    this.loading = true;
    this.packService.getActivePacks().subscribe({
      next: (data) => {
        this.packs = data;
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load packs';
        this.loading = false;
      }
    });
  }



  filterByCategory(categoryId: number | null): void {
    this.selectedCategoryId = categoryId;
    this.applyFilters();
  }

  applyFilters(): void {
    this.filteredPacks = this.packs.filter(pack => {
      return this.selectedCategoryId === null || pack.categoryId === this.selectedCategoryId;
    });
    this.currentPage = 1;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredPacks.length / this.pageSize));
  }

  get paginatedPacks(): Pack[] {
    const startIndex = (this.currentPage - 1) * this.pageSize;
    return this.filteredPacks.slice(startIndex, startIndex + this.pageSize);
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
  }

  goToPreviousPage(): void {
    if (this.currentPage > 1) this.currentPage--;
  }

  goToNextPage(): void {
    if (this.currentPage < this.totalPages) this.currentPage++;
  }

  openTelegramApp(): void {
    window.location.href = this.telegramAppUrl;
  }

  getDiscount(pack: Pack): number {
    if (!pack.originalPrice || pack.originalPrice === 0) return 0;
    return Math.round(((pack.originalPrice - pack.salePrice) / pack.originalPrice) * 100);
  }

  getLevelIcon(level: string): string {
    switch (level) {
      case 'BEGINNER': return '🟢';
      case 'INTERMEDIATE': return '🟡';
      case 'ADVANCED': return '🔴';
      default: return '⚪';
    }
  }


  getLevelLabel(level: string): string {
    switch (level) {
      case 'BEGINNER':
        return 'Beginner';
      case 'INTERMEDIATE':
        return 'Intermediate';
      case 'ADVANCED':
        return 'Advanced';
      default:
        return level;
    }
  }

  isTtsLoading(packId: number): boolean {
    return this.ttsLoadingPackId === packId;
  }

  isTtsPlaying(packId: number): boolean {
    return this.ttsPlayingPackId === packId;
  }

  getTtsButtonLabel(packId: number): string {
    if (this.isTtsLoading(packId)) {
      return 'Generating...';
    }
    if (this.isTtsPlaying(packId)) {
      return 'Stop Audio';
    }
    return 'Text to Speech';
  }

  togglePackTextToSpeech(pack: Pack): void {
    this.error = '';
    if (!this.isSpeechSynthesisSupported()) {
      this.error = 'Text-to-speech is not supported in this browser.';
      return;
    }

    if (this.isTtsLoading(pack.id)) return;

    if (this.isTtsPlaying(pack.id)) {
      this.stopCurrentSpeech();
      return;
    }

    this.stopCurrentSpeech();
    this.ttsLoadingPackId = pack.id;

    const utterance = new SpeechSynthesisUtterance(this.buildPackSpeechText(pack));
    utterance.lang = 'en-US';
    utterance.rate = 1;
    utterance.pitch = 1;
    utterance.volume = 1;

    const voices = window.speechSynthesis.getVoices();
    const englishVoice = voices.find(v => v.lang?.toLowerCase().startsWith('en'));
    if (englishVoice) {
      utterance.voice = englishVoice;
    }

    utterance.onstart = () => {
      this.ttsLoadingPackId = null;
      this.ttsPlayingPackId = pack.id;
    };

    utterance.onend = () => this.resetSpeechState();
    utterance.onerror = () => {
      this.error = 'Unable to play text-to-speech audio.';
      this.resetSpeechState();
    };

    this.currentUtterance = utterance;
    window.speechSynthesis.speak(utterance);
  }

  private isSpeechSynthesisSupported(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window;
  }

  private stopCurrentSpeech(): void {
    if (this.isSpeechSynthesisSupported() && (window.speechSynthesis.speaking || window.speechSynthesis.pending)) {
      window.speechSynthesis.cancel();
    }
    this.resetSpeechState();
  }

  private resetSpeechState(): void {
    this.ttsLoadingPackId = null;
    this.ttsPlayingPackId = null;
    this.currentUtterance = null;
  }

  private buildPackSpeechText(pack: Pack): string {
    return [
      `Pack title: ${pack.title}.`,
      pack.description ? `${pack.description}.` : '',
      `Difficulty level: ${this.getLevelLabel(pack.level)}.`,
      `Estimated duration: ${pack.durationHours} hours.`,
      `Current price: ${pack.salePrice} dollars.`
    ]
      .filter(Boolean)
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  ngOnDestroy(): void {
    this.stopCurrentSpeech();
  }
}

