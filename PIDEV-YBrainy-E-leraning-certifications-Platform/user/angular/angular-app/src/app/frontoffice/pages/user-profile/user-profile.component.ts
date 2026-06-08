import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';

import { UserStateService } from '../../services/user-state.service';
import { UserApiService } from '../../services/user-api.service';
import { XpEventResponse, UserProfileResponse } from '../../models/forum.models';

@Component({
  selector: 'app-user-profile',
  standalone: false,
  templateUrl: './user-profile.component.html',
  styleUrl: './user-profile.component.css',
  host: { style: 'display:block' },
})
export class UserProfileComponent implements OnInit {
  profile: UserProfileResponse | null = null;
  recent: XpEventResponse[] = [];
  loading = true;
  error: string | null = null;

  constructor(public userState: UserStateService, private userApi: UserApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    const userId = this.userState.currentUserId;

    this.userApi.getProfile(userId).subscribe({
      next: (p) => {
        this.profile = p;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.error = e?.message ?? 'Failed to load profile';
      },
    });

    this.userApi.getRecentXp(userId).subscribe({
      next: (r) => {
        this.recent = r;
      },
    });
  }
}
