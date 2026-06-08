import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';

import { UserStateService } from '../services/user-state.service';

@Component({
  selector: 'app-level-bar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './level-bar.component.html',
  styleUrl: './level-bar.component.css',
  host: { style: 'display:block' },
})
export class LevelBarComponent implements OnInit {
  constructor(public userState: UserStateService) {}

  ngOnInit(): void {
    this.userState.refresh();
  }

  trackById(_: number, item: { id: number }): number {
    return item.id;
  }
}
