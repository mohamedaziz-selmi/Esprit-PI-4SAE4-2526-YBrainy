import { Component } from '@angular/core';

@Component({
  selector: 'app-partners',
  standalone: false,
  templateUrl: './partners.component.html',
  styleUrl: './partners.component.css',
  host: { 'style': 'display:block' }
})
export class PartnersComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

