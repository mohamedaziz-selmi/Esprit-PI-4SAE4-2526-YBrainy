import { Component } from '@angular/core';

@Component({
  selector: 'app-why-ybrainy',
  standalone: false,
  templateUrl: './why-ybrainy.component.html',
  styleUrl: './why-ybrainy.component.css',
  host: { 'style': 'display:block' }
})
export class WhyYbrainyComponent {
  readonly assets = 'assets/frontoffice/www.ciklum.com/';
}

