import { Component } from '@angular/core';

@Component({
  selector: 'app-prefooter-cta',
  standalone: false,
  templateUrl: './prefooter-cta.component.html',
  styleUrl: './prefooter-cta.component.css',
  host: { 'style': 'display:block' }
})
export class PrefooterCtaComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

