import { Component, OnDestroy, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';

@Component({
  selector: 'app-backoffice-dashboard',
  templateUrl: './backoffice-dashboard.component.html',
  styleUrl: './backoffice-dashboard.component.css',
  host: { style: 'display:block' },
})
export class BackofficeDashboardComponent implements OnInit, OnDestroy {
  dashboardUrl: string = 'assets/backoffice/index.html';
  safeDashboardUrl: SafeResourceUrl;
  private readonly sub = new Subscription();

  constructor(
    private sanitizer: DomSanitizer,
    private route: ActivatedRoute
  ) {
    this.safeDashboardUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.dashboardUrl);
  }

  ngOnInit(): void {
    this.sub.add(
      combineLatest([this.route.data, this.route.queryParamMap]).subscribe(([data, qpm]) => {
        const page = (data?.['page'] as string | undefined) ?? 'index.html';
        const qs = qpm.keys
          .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(qpm.get(k) ?? '')}`)
          .join('&');
        this.dashboardUrl = `assets/backoffice/${page}${qs ? `?${qs}` : ''}`;
        this.safeDashboardUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.dashboardUrl);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }
}


