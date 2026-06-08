import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { FrontofficeStaticPageService } from '../../services/frontoffice-static-page.service';
import { FrontofficeUiInitService } from '../../services/frontoffice-ui-init.service';

@Component({
  selector: 'app-service-detail',
  templateUrl: './service-detail.component.html',
  styleUrls: ['./service-detail.component.css'],
  host: { style: 'display:block' },
})
export class ServiceDetailComponent implements AfterViewInit, OnDestroy {
  mainHtml: SafeHtml | null = null;
  private previousBodyClass: string | null = null;
  private readonly sub = new Subscription();
  private loadSeq = 0;

  constructor(
    private staticPage: FrontofficeStaticPageService,
    private uiInit: FrontofficeUiInitService,
    private route: ActivatedRoute
  ) {}

  ngAfterViewInit(): void {
    this.previousBodyClass = document.body.getAttribute('class');

    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        const slug = pm.get('slug') ?? '';
        void this.load(slug);
      })
    );
  }

  private async load(slug: string): Promise<void> {
    if (!slug) return;
    const seq = ++this.loadSeq;

    // Show loader while switching between services
    this.mainHtml = null;

    try {
      const url = `assets/frontoffice/www.ciklum.com/services/${encodeURIComponent(slug)}/index.html`;
      const { bodyClass, mainHtml } = await this.staticPage.load(url, 'main.main-container');
      if (seq !== this.loadSeq) return;

      document.body.setAttribute('class', bodyClass || '');
      this.mainHtml = mainHtml;
      this.uiInit.initAfterDomPaint();
      window.scrollTo({ top: 0, behavior: 'auto' });
    } catch (e) {
      // Keep a minimal, non-blocking error state
      console.error(e);
      if (seq !== this.loadSeq) return;
      document.body.setAttribute('class', this.previousBodyClass || '');
      this.mainHtml = null;
    }
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    if (this.previousBodyClass !== null) {
      document.body.setAttribute('class', this.previousBodyClass);
    }
  }
}


