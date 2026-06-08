import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: false,
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.css',
  host: { 'style': 'display:block' }
})
export class FooterComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
  currentYear = new Date().getFullYear();
}

