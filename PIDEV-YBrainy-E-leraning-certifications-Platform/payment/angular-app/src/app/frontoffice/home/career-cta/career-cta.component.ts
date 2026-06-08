import { Component } from '@angular/core';

@Component({
  selector: 'app-career-cta',
  standalone: false,
  templateUrl: './career-cta.component.html',
  styleUrl: './career-cta.component.css',
  host: { 'style': 'display:block' }
})
export class CareerCtaComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

