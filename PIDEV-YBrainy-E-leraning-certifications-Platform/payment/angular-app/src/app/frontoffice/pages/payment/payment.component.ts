import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { FrontofficeStaticPageService } from '../../services/frontoffice-static-page.service';
import { FrontofficeUiInitService } from '../../services/frontoffice-ui-init.service';
import { PackService } from '../../../services/pack.service';
import { Pack } from '../../../models/pack.model';

@Component({
  selector: 'app-payment',
  standalone: false,
  templateUrl: './payment.component.html',
  styleUrls: ['./payment.component.css'],
  host: { 'style': 'display:block' }
})
export class PaymentComponent implements AfterViewInit, OnDestroy {
  mainHtml: SafeHtml | null = null;
  private previousBodyClass: string | null = null;

  // Pack data
  packs: Pack[] = [];
  packsLoading = true;
  packsError = '';
  packsInjectionReady = false;

  constructor(
    private staticPage: FrontofficeStaticPageService,
    private uiInit: FrontofficeUiInitService,
    private packService: PackService
  ) { }

  async ngAfterViewInit(): Promise<void> {
    const { bodyClass, mainHtml } = await this.staticPage.load(
      'assets/frontoffice/www.ciklum.com/industries/travel-hospitality/index.html',
      'main.main-container'
    );

    this.previousBodyClass = document.body.getAttribute('class');
    document.body.setAttribute('class', bodyClass || '');

    this.mainHtml = mainHtml;
    this.packsInjectionReady = true;
    this.loadPacks();
    this.uiInit.initAfterDomPaint();
  }

  loadPacks(): void {
    console.log('🔍 loadPacks() called');
    this.packsLoading = true;
    this.packService.getActivePacks().subscribe({
      next: (data) => {
        console.log('✅ Packs loaded from API:', data);
        console.log('📊 Number of packs:', data.length);
        this.packs = data;
        this.packsLoading = false;
        console.log('🎯 Component packs array:', this.packs);
        console.log('🎯 packsLoading:', this.packsLoading);
        console.log('🎯 packsInjectionReady:', this.packsInjectionReady);
        // Reinitialize Swiper after packs are loaded
        setTimeout(() => this.uiInit.initAfterDomPaint(), 100);
      },
      error: (err) => {
        console.error('❌ Error loading packs:', err);
        console.error('❌ Error details:', JSON.stringify(err));
        this.packsError = 'Failed to load learning packs';
        this.packsLoading = false;
      }
    });
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

  ngOnDestroy(): void {
    if (this.previousBodyClass !== null) {
      document.body.setAttribute('class', this.previousBodyClass);
    }
  }
}

