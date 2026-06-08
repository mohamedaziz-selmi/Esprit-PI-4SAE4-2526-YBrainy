import { Component } from '@angular/core';

@Component({
  selector: 'app-learning-paths',
  standalone: false,
  templateUrl: './learning-paths.component.html',
  styleUrl: './learning-paths.component.css',
  host: { 'style': 'display:block' }
})
export class LearningPathsComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

