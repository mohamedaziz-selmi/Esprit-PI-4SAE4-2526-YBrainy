import { Component, OnInit } from '@angular/core';
import { AuthService, AuthUser } from '../../services/auth.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css'],
})
export class ProfileComponent implements OnInit {
  user: AuthUser | null = null;

  // Profile image — stored in localStorage as a base64 data-URL
  private readonly AVATAR_KEY = 'yb_profile_avatar';
  avatarUrl: string | null = null;

  // Edit fields
  displayName = '';
  editMode = false;

  // Upload state
  uploadError = '';
  uploadSuccess = false;
  private uploadSuccessTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.user = this.authService.getUser();
    this.displayName = this.user?.name ?? '';
    const stored = localStorage.getItem(this.AVATAR_KEY);
    if (stored) {
      this.avatarUrl = stored;
    }
  }

  onAvatarFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploadError = '';

    if (!file.type.startsWith('image/')) {
      this.uploadError = 'Please select an image file (JPG, PNG, WebP, etc.)';
      return;
    }
    if (file.size > 4 * 1024 * 1024) {
      this.uploadError = 'Image must be smaller than 4 MB.';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      this.avatarUrl = dataUrl;
      localStorage.setItem(this.AVATAR_KEY, dataUrl);
      this.showUploadSuccess();
    };
    reader.onerror = () => {
      this.uploadError = 'Failed to read image. Please try another file.';
    };
    reader.readAsDataURL(file);

    // reset input so same file can be re-selected
    input.value = '';
  }

  removeAvatar(): void {
    this.avatarUrl = null;
    localStorage.removeItem(this.AVATAR_KEY);
  }

  saveName(): void {
    if (!this.displayName.trim()) return;
    const raw = localStorage.getItem('yb_auth_user');
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        parsed.name = this.displayName.trim();
        localStorage.setItem('yb_auth_user', JSON.stringify(parsed));
        if (this.user) {
          this.user = { ...this.user, name: this.displayName.trim() };
        }
      } catch { /* ignore */ }
    }
    this.editMode = false;
  }

  logout(): void {
    this.authService.clearSession();
    window.location.href = '/login';
  }

  get userInitials(): string {
    const name = this.user?.name ?? '';
    return name
      .split(' ')
      .map((w) => w[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);
  }

  private showUploadSuccess(): void {
    this.uploadSuccess = true;
    if (this.uploadSuccessTimer) clearTimeout(this.uploadSuccessTimer);
    this.uploadSuccessTimer = setTimeout(() => {
      this.uploadSuccess = false;
    }, 2500);
  }
}
