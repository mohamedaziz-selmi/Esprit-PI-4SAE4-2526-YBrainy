import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-backoffice-nav',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './backoffice-nav.component.html',
  styleUrl: './backoffice-nav.component.css',
  host: { style: 'display:block' },
})
export class BackofficeNavComponent {}
