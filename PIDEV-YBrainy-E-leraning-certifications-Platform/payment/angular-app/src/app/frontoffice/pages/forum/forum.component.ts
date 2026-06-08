import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { FrontofficeStaticPageService } from '../../services/frontoffice-static-page.service';
import { FrontofficeUiInitService } from '../../services/frontoffice-ui-init.service';

@Component({
  selector: 'app-forum',
  standalone: false,
  templateUrl: './forum.component.html',
  styleUrls: ['./forum.component.css'],
  host: { 'style': 'display:block' }
})
export class ForumComponent implements AfterViewInit, OnDestroy {
  mainHtml: SafeHtml | null = null;
  private previousBodyClass: string | null = null;

  constructor(
    private staticPage: FrontofficeStaticPageService,
    private uiInit: FrontofficeUiInitService
  ) {}

  async ngAfterViewInit(): Promise<void> {
    const { bodyClass, mainHtml } = await this.staticPage.load(
      'assets/frontoffice/www.ciklum.com/blogs/index.html',
      'main.main-container'
    );

    this.previousBodyClass = document.body.getAttribute('class');
    document.body.setAttribute('class', bodyClass || '');

    this.mainHtml = mainHtml;
    this.uiInit.initAfterDomPaint();
  }

  ngOnDestroy(): void {
    if (this.previousBodyClass !== null) {
      document.body.setAttribute('class', this.previousBodyClass);
    }
  }
}

