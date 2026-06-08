import { Component } from '@angular/core';

@Component({
  selector: 'app-trust-logos',
  standalone: false,
  templateUrl: './trust-logos.component.html',
  styleUrl: './trust-logos.component.css',
  host: { 'style': 'display:block' }
})
export class TrustLogosComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

