import { Component } from '@angular/core';

@Component({
  selector: 'app-layout',
  standalone: false,
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css',
  host: { 'style': 'display:block' }
})
export class LayoutComponent {}

