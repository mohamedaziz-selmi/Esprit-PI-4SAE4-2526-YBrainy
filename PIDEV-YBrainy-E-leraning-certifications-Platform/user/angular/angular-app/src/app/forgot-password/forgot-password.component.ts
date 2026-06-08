import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { environment } from '../../environments/environment';

interface ForgotPasswordResponse {
  accepted?: boolean;
  delivery?: string | null;
  devFallbackCode?: string | null;
}

const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('newPassword')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  return password && confirmPassword && password !== confirmPassword ? { passwordMismatch: true } : null;
};

const passwordPolicyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '');
  if (!value) return null;
  const hasUpper = /[A-Z]/.test(value);
  const hasLower = /[a-z]/.test(value);
  const hasNumber = /\d/.test(value);
  return hasUpper && hasLower && hasNumber ? null : { passwordPolicy: true };
};

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.css',
  host: { style: 'display:block' },
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);

  private readonly authBaseUrl = `${environment.apiBaseUrl}/api/auth`;
  private readonly brandLogoCandidates = [
    'assets/login/akademi.dexignlab.com/xhtml/images/logo-white.png',
    'assets/login/akademi.dexignlab.com/xhtml/images/qsdqs.png',
  ];
  private readonly illustrationCandidates = [
    'assets/backoffice/social-image.png',
    'assets/login/akademi.dexignlab.com/xhtml/images/1.jpg',
  ];
  private brandLogoIndex = 0;
  private illustrationIndex = 0;
  brandLogoSrc = this.brandLogoCandidates[this.brandLogoIndex];
  illustrationSrc = this.illustrationCandidates[this.illustrationIndex];

  readonly requestForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly resetForm = this.fb.nonNullable.group(
    {
      code: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(12)]],
      newPassword: ['', [Validators.required, Validators.minLength(8), passwordPolicyValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordMatchValidator }
  );

  submittingRequest = false;
  submittingReset = false;
  errorMessage = '';
  successMessage = '';
  resetSuccessMessage = '';
  requestedEmail = '';
  devFallbackCode = '';
  showBrandLogo = true;
  showIllustration = true;

  get isCodeStep(): boolean {
    return !!this.requestedEmail;
  }

  onSubmitRequest(): void {
    this.clearMessages();

    if (this.requestForm.invalid || this.submittingRequest) {
      this.requestForm.markAllAsTouched();
      return;
    }

    this.submittingRequest = true;
    const payload = this.requestForm.getRawValue();
    const email = payload.email.trim();

    this.http
      .post<ForgotPasswordResponse>(`${this.authBaseUrl}/forgot-password`, { email })
      .pipe(finalize(() => (this.submittingRequest = false)))
      .subscribe({
        next: (response) => {
          const fallbackCode = String(response?.devFallbackCode ?? '').trim();
          this.requestedEmail = email;
          this.devFallbackCode = fallbackCode;
          this.successMessage = fallbackCode
            ? 'Email is unavailable locally, but a verification code was generated below so you can continue now.'
            : 'Verification code sent. Enter the code from your email and set a new password.';
          this.resetSuccessMessage = '';
          this.resetForm.reset();
          if (fallbackCode) {
            this.resetForm.patchValue({ code: fallbackCode });
          }
        },
        error: (err) => {
          const raw = String(err?.error?.message ?? err?.message ?? 'Request failed');
          if (/authenticat|smtp|mail/i.test(raw)) {
            this.errorMessage = 'Email service is temporarily unavailable. Please try again later.';
          } else {
            this.errorMessage = raw;
          }
        },
      });
  }

  onSubmitReset(): void {
    this.errorMessage = '';
    this.resetSuccessMessage = '';

    if (!this.requestedEmail) {
      this.errorMessage = 'Please request a verification code first.';
      return;
    }

    if (this.resetForm.invalid || this.submittingReset) {
      this.resetForm.markAllAsTouched();
      return;
    }

    this.submittingReset = true;
    const payload = this.resetForm.getRawValue();

    this.http
      .post<void>(`${this.authBaseUrl}/forgot-password/reset-with-code`, {
        email: this.requestedEmail,
        code: payload.code.trim(),
        newPassword: payload.newPassword,
        confirmPassword: payload.confirmPassword,
      })
      .pipe(finalize(() => (this.submittingReset = false)))
      .subscribe({
        next: () => {
          this.resetSuccessMessage = 'Password reset successful. You can sign in now.';
          this.successMessage = '';
          this.errorMessage = '';
          this.devFallbackCode = '';
          this.resetForm.reset();
        },
        error: (err) => {
          const raw = String(err?.error?.message ?? err?.message ?? 'Password reset failed');
          this.errorMessage = raw;
        },
      });
  }

  resendCode(): void {
    if (!this.requestedEmail || this.submittingRequest) return;
    this.requestForm.controls.email.setValue(this.requestedEmail);
    this.onSubmitRequest();
  }

  backToEmailStep(): void {
    this.requestedEmail = '';
    this.devFallbackCode = '';
    this.resetForm.reset();
    this.clearMessages();
  }

  onBrandLogoError(): void {
    this.brandLogoIndex += 1;
    if (this.brandLogoIndex >= this.brandLogoCandidates.length) {
      this.showBrandLogo = false;
      return;
    }
    this.brandLogoSrc = this.brandLogoCandidates[this.brandLogoIndex];
  }

  onIllustrationError(): void {
    this.illustrationIndex += 1;
    if (this.illustrationIndex >= this.illustrationCandidates.length) {
      this.showIllustration = false;
      return;
    }
    this.illustrationSrc = this.illustrationCandidates[this.illustrationIndex];
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.resetSuccessMessage = '';
  }
}
