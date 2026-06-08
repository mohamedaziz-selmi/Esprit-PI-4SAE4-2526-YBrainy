import { Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';

import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../environments/environment';

type ChallengeMode = 'jigsaw' | 'history' | 'math' | 'logic';
type ChallengeKind = 'multiple_choice' | 'flag_jigsaw';

interface SignUpChallengeModeOption {
  key: ChallengeMode;
  label: string;
  description: string;
  recommended: boolean;
}

interface SignUpChallengeChoice {
  id: string;
  label: string;
}

interface SignUpChallengeJigsawPiece {
  id: string;
  correctIndex: number;
  displayOrder: number;
  row: number;
  column: number;
}

interface SignUpChallengeFlagJigsaw {
  countryName: string;
  flagDataUrl: string;
  rows: number;
  columns: number;
  pieces: SignUpChallengeJigsawPiece[];
}

interface SignUpChallengeResponse {
  token: string;
  prompt: string;
  helperText: string;
  challengeKind: ChallengeKind;
  selectedMode: ChallengeMode;
  recommendedMode: ChallengeMode;
  profileHint: string;
  recommendationReason: string;
  expiresInSeconds: number;
  availableModes: SignUpChallengeModeOption[];
  choices: SignUpChallengeChoice[];
  flagJigsaw: SignUpChallengeFlagJigsaw | null;
}

const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  return password && confirmPassword && password !== confirmPassword
    ? { passwordMismatch: true }
    : null;
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
  selector: 'app-signup-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.css',
  host: { style: 'display:block' },
})
export class SignUpPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly authBaseUrl = `${environment.apiBaseUrl}/api/auth`;
  private readonly avatarUploadUrl = `${environment.apiBaseUrl}/api/users/uploads/avatar`;
  private readonly challengeUrl = `${this.authBaseUrl}/signup/challenge`;

  readonly form = this.fb.nonNullable.group(
    {
      username: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
          Validators.maxLength(50),
          Validators.pattern(/^[a-zA-Z0-9._-]+$/),
        ],
      ],
      firstName: ['', [Validators.maxLength(50), Validators.pattern(/^[a-zA-Z\s'-]*$/)]],
      lastName: ['', [Validators.maxLength(50), Validators.pattern(/^[a-zA-Z\s'-]*$/)]],
      email: ['', [Validators.required, Validators.email]],
      password: [
        '',
        [Validators.required, Validators.minLength(8), Validators.maxLength(72), passwordPolicyValidator],
      ],
      confirmPassword: ['', [Validators.required]],
      dateOfBirth: [''],
      age: [null as number | null, [Validators.min(6), Validators.max(120)]],
      address: [''],
      city: [''],
      country: [''],
      sex: ['', [Validators.pattern(/^(|Male|Female|Other)$/)]],
      profilePicture: [''],
      companyName: [''],
      role: ['INSTRUCTOR', [Validators.required]],
    },
    { validators: passwordMatchValidator }
  );

  submitting = false;
  errorMessage = '';
  successMessage = '';
  showPassword = false;
  showConfirm = false;
  profilePicturePreview = '';
  profilePictureFileName = '';
  uploadingProfilePicture = false;
  challengeRequested = false;
  challengeLoading = false;
  challengeNeedsRefresh = false;
  challengeError = '';
  challengeTouched = false;
  selectedChallengeAnswer = '';
  signUpChallenge: SignUpChallengeResponse | null = null;
  jigsawPieces: SignUpChallengeJigsawPiece[] = [];
  selectedJigsawPieceId: string | null = null;
  readonly maxBirthDate = this.formatDateForInput(new Date());
  private latestChallengeRequestId = 0;

  constructor() {
    this.syncAgeFromDateOfBirth(this.form.controls.dateOfBirth.value);
    this.updateCompanyNameRules(this.form.controls.role.value);

    this.form.controls.dateOfBirth.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((dateOfBirth) => {
        this.syncAgeFromDateOfBirth(dateOfBirth);
        this.invalidateChallengeIfProfileChanged();
      });

    this.form.controls.role.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((role) => this.updateCompanyNameRules(role));

    this.form.controls.country.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.invalidateChallengeIfProfileChanged());
  }

  get isEnterpriseRole(): boolean {
    return this.form.controls.role.value === 'ENTERPRISE_USER';
  }

  get canStartChallenge(): boolean {
    const age = this.form.controls.age.value;
    const country = this.form.controls.country.value?.trim();
    return typeof age === 'number' && Number.isFinite(age) && age >= 6 && Boolean(country);
  }

  get isChallengeReady(): boolean {
    return Boolean(this.signUpChallenge?.token && this.selectedChallengeAnswer);
  }

  get challengeTriggerLabel(): string {
    if (this.challengeNeedsRefresh) {
      return 'Do captcha again';
    }
    return this.signUpChallenge ? 'Refresh captcha' : 'Do captcha';
  }

  get isJigsawMode(): boolean {
    return this.signUpChallenge?.challengeKind === 'flag_jigsaw' && !!this.signUpChallenge.flagJigsaw;
  }

  onSubmit(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.uploadingProfilePicture) {
      this.errorMessage = 'Please wait for the profile image upload to finish.';
      return;
    }
    if (this.challengeLoading) {
      this.errorMessage = 'Please wait for the personalized security check to finish loading.';
      return;
    }
    if (!this.signUpChallenge?.token || !this.selectedChallengeAnswer) {
      this.challengeTouched = true;
      this.errorMessage = 'Complete the personalized security check before creating your account.';
      return;
    }
    if (this.submitting) return;

    this.submitting = true;

    const payload = {
      ...this.form.getRawValue(),
      companyName: this.isEnterpriseRole ? this.form.controls.companyName.value : null,
      challengeToken: this.signUpChallenge.token,
      challengeMode: this.signUpChallenge.selectedMode,
      challengeAnswer: this.selectedChallengeAnswer,
    };

    this.http
      .post<{ userId: number; username: string; email: string; role: string }>(
        `${this.authBaseUrl}/signup`,
        payload
      )
      .subscribe({
        next: () => {
          this.successMessage = 'Account created successfully! Redirecting to login...';
          setTimeout(() => this.router.navigateByUrl('/login'), 1500);
        },
        error: (err) => {
          this.errorMessage = this.resolveApiError(err, 'Sign up failed');
          if (this.errorMessage.toLowerCase().includes('challenge') || this.errorMessage.toLowerCase().includes('security check')) {
            this.selectedChallengeAnswer = '';
            this.challengeTouched = false;
            this.loadSignUpChallenge(this.signUpChallenge?.selectedMode);
          }
          this.submitting = false;
        },
        complete: () => {
          this.submitting = false;
        },
      });
  }

  onProfilePictureFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (!file) {
      return;
    }

    if (!file.type.startsWith('image/')) {
      this.errorMessage = 'Please select an image file.';
      input.value = '';
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';
    this.profilePictureFileName = file.name;
    this.profilePicturePreview = URL.createObjectURL(file);
    this.form.controls.profilePicture.setValue('');
    this.form.controls.profilePicture.markAsDirty();
    this.uploadProfilePicture(file, input);
  }

  clearProfilePicture(): void {
    this.form.controls.profilePicture.setValue('');
    this.form.controls.profilePicture.markAsDirty();
    this.profilePicturePreview = '';
    this.profilePictureFileName = '';
    this.uploadingProfilePicture = false;
  }

  private uploadProfilePicture(file: File, input: HTMLInputElement): void {
    const formData = new FormData();
    formData.append('file', file);
    this.uploadingProfilePicture = true;

    this.http.post<{ url: string }>(this.avatarUploadUrl, formData).subscribe({
      next: (response) => {
        this.uploadingProfilePicture = false;
        this.form.controls.profilePicture.setValue(response?.url ?? '');
        this.form.controls.profilePicture.markAsDirty();
        this.successMessage = 'Profile image uploaded. It will be saved with your account.';
        input.value = '';
      },
      error: (err) => {
        this.uploadingProfilePicture = false;
        this.form.controls.profilePicture.setValue('');
        this.profilePicturePreview = '';
        this.profilePictureFileName = '';
        this.errorMessage = this.resolveApiError(err, 'Image upload failed');
        input.value = '';
      },
    });
  }

  onChallengeModeSelected(mode: ChallengeMode): void {
    if (this.challengeLoading || this.signUpChallenge?.selectedMode === mode) {
      return;
    }
    this.startChallenge(mode);
  }

  onDoCaptcha(): void {
    this.startChallenge();
  }

  selectChallengeAnswer(choiceId: string): void {
    if (this.isJigsawMode) {
      return;
    }
    this.selectedChallengeAnswer = choiceId;
    this.challengeTouched = true;
    this.challengeError = '';
    this.errorMessage = '';
  }

  onJigsawPieceClicked(pieceId: string): void {
    if (!this.isJigsawMode || this.challengeLoading) {
      return;
    }

    this.challengeTouched = true;
    this.challengeError = '';
    this.errorMessage = '';

    if (this.selectedJigsawPieceId === pieceId) {
      this.selectedJigsawPieceId = null;
      return;
    }

    if (!this.selectedJigsawPieceId) {
      this.selectedJigsawPieceId = pieceId;
      return;
    }

    const firstIndex = this.jigsawPieces.findIndex((piece) => piece.id === this.selectedJigsawPieceId);
    const secondIndex = this.jigsawPieces.findIndex((piece) => piece.id === pieceId);
    if (firstIndex < 0 || secondIndex < 0) {
      this.selectedJigsawPieceId = null;
      return;
    }

    [this.jigsawPieces[firstIndex], this.jigsawPieces[secondIndex]] = [
      this.jigsawPieces[secondIndex],
      this.jigsawPieces[firstIndex],
    ];
    this.selectedJigsawPieceId = null;
    this.syncJigsawAnswer();
  }

  getJigsawTileStyle(piece: SignUpChallengeJigsawPiece): Record<string, string> {
    const jigsaw = this.signUpChallenge?.flagJigsaw;
    if (!jigsaw) {
      return {};
    }

    const x = jigsaw.columns === 1 ? 0 : (piece.column / (jigsaw.columns - 1)) * 100;
    const y = jigsaw.rows === 1 ? 0 : (piece.row / (jigsaw.rows - 1)) * 100;

    return {
      'background-image': `url(${jigsaw.flagDataUrl})`,
      'background-size': `${jigsaw.columns * 100}% ${jigsaw.rows * 100}%`,
      'background-position': `${x}% ${y}%`,
      'background-repeat': 'no-repeat',
    };
  }

  private syncAgeFromDateOfBirth(dateOfBirth: string): void {
    const ageControl = this.form.controls.age;
    const calculatedAge = this.calculateAge(dateOfBirth);
    ageControl.setValue(calculatedAge, { emitEvent: false });
  }

  private updateCompanyNameRules(role: string): void {
    const companyControl = this.form.controls.companyName;
    if (role === 'ENTERPRISE_USER') {
      companyControl.setValidators([Validators.required, Validators.maxLength(120)]);
    } else {
      companyControl.clearValidators();
      companyControl.setValue('', { emitEvent: false });
    }
    companyControl.updateValueAndValidity({ emitEvent: false });
  }

  private startChallenge(preferredMode?: ChallengeMode): void {
    this.challengeRequested = true;
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.canStartChallenge) {
      this.challengeError = 'Choose your date of birth and country first, then click Do captcha.';
      return;
    }

    this.loadSignUpChallenge(preferredMode);
  }

  private loadSignUpChallenge(preferredMode?: ChallengeMode): void {
    const requestId = ++this.latestChallengeRequestId;
    this.challengeLoading = true;
    this.challengeRequested = true;
    this.challengeNeedsRefresh = false;
    this.challengeError = '';
    this.challengeTouched = false;
    this.selectedChallengeAnswer = '';
    this.selectedJigsawPieceId = null;
    this.jigsawPieces = [];
    this.signUpChallenge = null;

    const payload = {
      age: this.form.controls.age.value,
      country: this.form.controls.country.value?.trim() || null,
      preferredMode: preferredMode ?? null,
    };

    this.http.post<SignUpChallengeResponse>(this.challengeUrl, payload).subscribe({
      next: (challenge) => {
        if (requestId !== this.latestChallengeRequestId) {
          return;
        }
        this.signUpChallenge = challenge;
        this.challengeLoading = false;
        this.configureChallengeState(challenge);
      },
      error: (err) => {
        if (requestId !== this.latestChallengeRequestId) {
          return;
        }
        this.challengeLoading = false;
        this.challengeError = this.resolveApiError(err, 'Could not prepare the personalized security check.');
      },
    });
  }

  private configureChallengeState(challenge: SignUpChallengeResponse): void {
    if (challenge.challengeKind === 'flag_jigsaw' && challenge.flagJigsaw) {
      this.jigsawPieces = [...challenge.flagJigsaw.pieces].sort((left, right) => left.displayOrder - right.displayOrder);
      this.selectedJigsawPieceId = null;
      this.syncJigsawAnswer();
      return;
    }

    this.jigsawPieces = [];
    this.selectedJigsawPieceId = null;
    this.selectedChallengeAnswer = '';
  }

  private syncJigsawAnswer(): void {
    if (!this.isJigsawMode) {
      this.selectedChallengeAnswer = '';
      return;
    }

    const solved = this.jigsawPieces.every((piece, index) => piece.correctIndex === index);
    this.selectedChallengeAnswer = solved ? this.jigsawPieces.map((piece) => piece.id).join('|') : '';
  }

  private invalidateChallengeIfProfileChanged(): void {
    if (!this.challengeRequested && !this.signUpChallenge && !this.challengeLoading && !this.challengeNeedsRefresh) {
      return;
    }

    this.latestChallengeRequestId += 1;
    this.challengeRequested = false;
    this.challengeNeedsRefresh = true;
    this.challengeLoading = false;
    this.signUpChallenge = null;
    this.selectedChallengeAnswer = '';
    this.challengeTouched = false;
    this.selectedJigsawPieceId = null;
    this.jigsawPieces = [];
    this.challengeError = 'Your age or country changed. Click Do captcha again to refresh the challenge.';
  }

  private calculateAge(dateOfBirth: string): number | null {
    if (!dateOfBirth) {
      return null;
    }

    const dob = new Date(`${dateOfBirth}T00:00:00`);
    if (Number.isNaN(dob.getTime())) {
      return null;
    }

    const today = new Date();
    let age = today.getFullYear() - dob.getFullYear();
    const beforeBirthday =
      today.getMonth() < dob.getMonth() ||
      (today.getMonth() === dob.getMonth() && today.getDate() < dob.getDate());

    if (beforeBirthday) {
      age -= 1;
    }

    return age >= 0 ? age : null;
  }

  private formatDateForInput(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private resolveApiError(err: unknown, fallback: string): string {
    const backend = (err as any)?.error;
    if (typeof backend?.message === 'string' && backend.message.trim()) {
      return backend.message.trim();
    }

    if (backend && typeof backend === 'object' && !Array.isArray(backend)) {
      const firstFieldError = Object.values(backend).find(
        (value): value is string => typeof value === 'string' && value.trim().length > 0
      );
      if (firstFieldError) {
        return firstFieldError.trim();
      }
    }

    const genericMessage = (err as any)?.message;
    if (typeof genericMessage === 'string' && genericMessage.trim()) {
      return genericMessage.trim();
    }

    return fallback;
  }
}
