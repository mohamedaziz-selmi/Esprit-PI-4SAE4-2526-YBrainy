import { Component } from '@angular/core';

@Component({
  selector: 'app-success-stories',
  standalone: false,
  templateUrl: './success-stories.component.html',
  styleUrl: './success-stories.component.css',
  host: { 'style': 'display:block' }
})
export class SuccessStoriesComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

