import { Component } from '@angular/core';

@Component({
  selector: 'app-designed-for-learners',
  standalone: false,
  templateUrl: './designed-for-learners.component.html',
  styleUrl: './designed-for-learners.component.css',
  host: { 'style': 'display:block' }
})
export class DesignedForLearnersComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

