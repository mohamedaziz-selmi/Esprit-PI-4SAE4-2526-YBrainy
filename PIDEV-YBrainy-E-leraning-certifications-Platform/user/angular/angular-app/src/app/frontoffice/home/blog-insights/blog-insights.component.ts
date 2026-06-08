import { Component } from '@angular/core';

@Component({
  selector: 'app-blog-insights',
  standalone: false,
  templateUrl: './blog-insights.component.html',
  styleUrl: './blog-insights.component.css',
  host: { 'style': 'display:block' }
})
export class BlogInsightsComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

