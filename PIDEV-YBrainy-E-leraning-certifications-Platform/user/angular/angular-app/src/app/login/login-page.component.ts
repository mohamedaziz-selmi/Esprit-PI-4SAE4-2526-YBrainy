import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UserSessionService } from '../tracking/user-session.service';
import { getKeycloak, loginWithGoogle, signInWithFaceBiometric, signInWithPassword } from '../auth/keycloak.service';
import { FaceCaptureDialogComponent } from '../shared/face-capture-dialog/face-capture-dialog.component';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, FaceCaptureDialogComponent],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.css',
  host: { style: 'display:block' },
})
export class LoginPageComponent {
  private static readonly FACE_SCAN_INTERVAL_MS = 1500;
  private static readonly BAN_APPEAL_MIN_LENGTH = 10;
  private static readonly BAN_APPEAL_MAX_LENGTH = 1000;

  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(UserSessionService);
  private readonly appealsApiBase = `${environment.apiBaseUrl}/api/ban-appeals`;

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submitting = false;
  errorMessage = '';
  showPassword = false;
  faceDialogOpen = false;
  faceSubmitting = false;
  banAppealSubmitting = false;
  banAppealDescription = '';
  banAppealMessage = '';
  showBanAppealForm = false;
  readonly faceScanMessage = `Live face login is running. A new frame is checked every ${LoginPageComponent.FACE_SCAN_INTERVAL_MS / 1000} seconds.`;

  async onSubmit(): Promise<void> {
    this.errorMessage = '';
    this.banAppealMessage = '';
    this.showBanAppealForm = false;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.submitting) return;

    this.submitting = true;

    try {
      const { email, password } = this.form.getRawValue();
      const auth = await signInWithPassword(email, password);
      const role = String(auth.role ?? '').toUpperCase() || 'INSTRUCTOR';

      this.session.set({
        userId: typeof auth.userId === 'number' ? auth.userId : Number.NaN,
        role,
        email: auth.email ?? email,
        username: auth.username,
      });
      this.showBanAppealForm = false;
      this.banAppealDescription = '';

      await this.navigateAfterLogin(role);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Login failed. Please try again.';
      this.errorMessage = message;
      this.showBanAppealForm = this.shouldShowBanAppealForm(message);
    } finally {
      this.submitting = false;
    }
  }

  onGoogleLogin(): void {
    this.errorMessage = '';
    loginWithGoogle(this.getPostLoginRedirectUri()).catch(() => {
      this.errorMessage = 'Unable to start login. Please try again.';
    });
  }

  onFaceIdLogin(): void {
    this.errorMessage = '';
    this.faceDialogOpen = true;
  }

  closeFaceDialog(): void {
    if (this.faceSubmitting) {
      return;
    }
    this.faceDialogOpen = false;
  }

  async onFaceImageConfirmed(image: Blob): Promise<void> {
    if (this.faceSubmitting) {
      return;
    }

    this.errorMessage = '';
    this.faceSubmitting = true;

    try {
      await signInWithFaceBiometric(image, this.getPostLoginRedirectUri());
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Face sign-in failed. Please try again.';

      if (this.isRetryableFaceSignInError(message)) {
        return;
      }

      this.faceDialogOpen = false;
      this.errorMessage = message;
    } finally {
      this.faceSubmitting = false;
    }
  }

  async ngOnInit(): Promise<void> {
    try {
      const kc = getKeycloak();
      if (!kc?.authenticated || !kc.tokenParsed) return;

      const parsed: any = kc.tokenParsed;
      const roles: string[] = parsed?.realm_access?.roles ?? [];
      const role = (roles[0] ?? '').toUpperCase();

      this.session.set({
        userId: Number.NaN,
        role,
        email: parsed?.email,
        username: parsed?.preferred_username,
      });

      await this.navigateAfterLogin(role);
    } catch {
      // ignore
    }
  }

  private async navigateAfterLogin(role: string): Promise<void> {
    const redirect = this.route.snapshot.queryParamMap.get('redirect');
    if (redirect) {
      try {
        const normalized = new URL(redirect, window.location.origin);
        if (normalized.origin === window.location.origin) {
          const path = `${normalized.pathname}${normalized.search}${normalized.hash}`;
          await this.router.navigateByUrl(path || '/');
          return;
        }
      } catch {
        if (redirect.startsWith('/')) {
          await this.router.navigateByUrl(redirect);
          return;
        }
      }
    }

    await this.router.navigateByUrl('/');
  }

  private getPostLoginRedirectUri(): string {
    const redirect = this.route.snapshot.queryParamMap.get('redirect');
    if (!redirect) {
      return `${window.location.origin}/`;
    }

    try {
      const normalized = new URL(redirect, window.location.origin);
      return normalized.origin === window.location.origin
        ? normalized.toString()
        : `${window.location.origin}/`;
    } catch {
      return redirect.startsWith('/')
        ? `${window.location.origin}${redirect}`
        : `${window.location.origin}/`;
    }
  }

  async submitBanAppeal(): Promise<void> {
    this.banAppealMessage = '';
    if (this.banAppealSubmitting) {
      return;
    }

    const email = this.form.controls.email.value.trim();
    const description = this.banAppealDescription.trim();

    if (!email || this.form.controls.email.invalid) {
      this.form.controls.email.markAsTouched();
      this.banAppealMessage = 'Enter the banned account email before submitting an appeal.';
      return;
    }

    if (description.length < LoginPageComponent.BAN_APPEAL_MIN_LENGTH) {
      this.banAppealMessage = 'Appeal description must be at least 10 characters.';
      return;
    }

    if (description.length > LoginPageComponent.BAN_APPEAL_MAX_LENGTH) {
      this.banAppealMessage = 'Appeal description must be 1000 characters or less.';
      return;
    }

    this.banAppealSubmitting = true;
    try {
      await firstValueFrom(
        this.http.post(`${this.appealsApiBase}/public`, {
          email,
          description,
        })
      );
      this.banAppealDescription = '';
      this.banAppealMessage = 'Ban appeal submitted successfully. Admin will review your request.';
    } catch (error: any) {
      const backend = error?.error;
      const validationMapMessage =
        backend && typeof backend === 'object' && !Array.isArray(backend)
          ? Object.entries(backend).find(([key, value]) =>
              key !== 'timestamp' &&
              key !== 'status' &&
              key !== 'error' &&
              typeof value === 'string' &&
              value.trim().length > 0
            )?.[1]
          : null;

      const message = String(
        (typeof backend === 'string' && backend.trim()) ||
          (typeof backend?.message === 'string' && backend.message.trim()) ||
          validationMapMessage ||
          error?.message ||
          'Unable to submit ban appeal. Please try again.'
      );
      this.banAppealMessage = message;
    } finally {
      this.banAppealSubmitting = false;
    }
  }

  private isRetryableFaceSignInError(message: string): boolean {
    const normalized = message.toLowerCase();
    return normalized.includes('face not recognized') || normalized.includes('no face');
  }

  private shouldShowBanAppealForm(message: string): boolean {
    const normalized = String(message ?? '').toLowerCase();
    if (
      normalized.includes('appeal submitted') ||
      normalized.includes('wait for admin response') ||
      normalized.includes('appeal was rejected') ||
      normalized.includes('still banned for')
    ) {
      return false;
    }
    return (
      normalized.includes('restricted') ||
      normalized.includes('disabled') ||
      normalized.includes('ban appeal') ||
      normalized.includes('banned')
    );
  }
}
