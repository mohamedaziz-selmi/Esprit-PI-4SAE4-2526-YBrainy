import { Component } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

@Component({
  selector: 'app-layout',
  standalone: false,
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css',
  host: { 'style': 'display:block' }
})
export class LayoutComponent {
  hideChrome = false;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.updateChromeVisibility(this.router.url);
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.updateChromeVisibility(e.urlAfterRedirects));
  }

  private updateChromeVisibility(url: string): void {
    const clean = (url || '').split('?')[0].split('#')[0];
    this.hideChrome = clean === '/profile';
  }
}

