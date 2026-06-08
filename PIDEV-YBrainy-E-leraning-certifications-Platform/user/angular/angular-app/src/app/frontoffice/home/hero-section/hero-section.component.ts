import { Component } from '@angular/core';

@Component({
  selector: 'app-hero-section',
  standalone: false,
  templateUrl: './hero-section.component.html',
  styleUrl: './hero-section.component.css',
  host: { 'style': 'display:block' }
})
export class HeroSectionComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

