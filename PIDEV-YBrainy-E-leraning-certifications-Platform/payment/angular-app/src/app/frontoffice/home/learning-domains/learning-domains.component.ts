import { Component } from '@angular/core';

@Component({
  selector: 'app-learning-domains',
  standalone: false,
  templateUrl: './learning-domains.component.html',
  styleUrl: './learning-domains.component.css',
  host: { 'style': 'display:block' }
})
export class LearningDomainsComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

