import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { UserProfile, UserService } from '../../services/user.service';
import { environment } from '../../../../environments/environment';
import { FaceCaptureDialogComponent } from '../../../shared/face-capture-dialog/face-capture-dialog.component';

interface ProfileWarning {
  warningId: number;
  reason: string;
  severity?: string | null;
  issuedDate?: string | null;
  issuedBy?: string | null;
  userId: number;
}

interface ProfileBanAppeal {
  appealId: number;
  description: string;
  appealStatus?: string | null;
  submittedDate?: string | null;
  resolvedDate?: string | null;
  reviewedBy?: string | null;
  userId: number;
}

type ProfileFieldKey =
  | 'firstName'
  | 'lastName'
  | 'username'
  | 'age'
  | 'dateOfBirth'
  | 'sex'
  | 'address'
  | 'city'
  | 'country'
  | 'profilePicture'
  | 'companyName';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, FaceCaptureDialogComponent],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  private static readonly USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,30}$/;
  private static readonly NAME_PATTERN = /^[A-Za-z\s'-]{1,60}$/;
  private static readonly PASSWORD_POLICY_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

  user: UserProfile | null = null;
  readonly maxBirthDate = new Date().toISOString().slice(0, 10);
  saving = false;
  avatarUploading = false;
  toastVisible = false;
  toastMessage = '';
  toastError = false;
  warningsLoading = false;
  appealsLoading = false;
  warningsLoadFailed = false;
  appealsLoadFailed = false;
  appealSubmitting = false;
  faceBiometricSaving = false;
  changingPassword = false;
  faceDialogOpen = false;
  profileSubmitAttempted = false;
  profileValidationErrors: string[] = [];
  profileFieldErrors: Partial<Record<ProfileFieldKey, string[]>> = {};
  passwordValidationError = '';
  appealValidationError = '';

  editModel: Partial<UserProfile> = {};
  passwordModel = { newPwd: '', confirmPwd: '' };
  appealDescription = '';
  warnings: ProfileWarning[] = [];
  appeals: ProfileBanAppeal[] = [];

  private readonly warningsApiBase = `${environment.apiBaseUrl}/api/warnings`;
  private readonly appealsApiBase = `${environment.apiBaseUrl}/api/ban-appeals`;
  private readonly avatarUploadApiBase = `${environment.apiBaseUrl}/api/users/uploads/avatar`;

  constructor(
    private http: HttpClient,
    private userService: UserService,
    private router: Router,
    private location: Location
  ) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.userService.getCurrentUser().subscribe({
      next: (data: UserProfile) => {
        this.user = data;
        this.resetEditModelFromUser(data);
        this.profileSubmitAttempted = false;
        this.profileValidationErrors = [];
        this.profileFieldErrors = {};
        this.passwordValidationError = '';
        this.appealValidationError = '';
        this.loadModerationPanels(data.userId);
      },
      error: () => this.showToast('Could not load profile. Please try again.', true),
    });
  }

  goBack(): void {
    if (window.history.length > 1) {
      this.location.back();
      return;
    }

    const fallback = this.router.url.startsWith('/dashboard') ? '/dashboard' : '/';
    this.router.navigate([fallback]);
  }

  getInitials(): string {
    if (!this.user) return '?';
    const f = this.user.firstName?.[0] ?? '';
    const l = this.user.lastName?.[0] ?? '';
    const initials = (f + l).toUpperCase();
    if (initials) return initials;
    const usernameInitial = this.user.username?.[0]?.toUpperCase();
    return usernameInitial || '?';
  }

  onAvatarChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      input.value = '';
      this.showToast('Please choose an image file.', true);
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    this.avatarUploading = true;

    this.http.post<{ url: string }>(this.avatarUploadApiBase, formData).subscribe({
      next: (response) => {
        this.avatarUploading = false;
        this.editModel.profilePicture = response?.url ?? '';
        if (this.profileSubmitAttempted) {
          this.profileValidationErrors = this.validateProfileInputs();
        }
        this.showToast('Avatar uploaded. Click Save profile to apply it to your account.');
        input.value = '';
      },
      error: (err: HttpErrorResponse) => {
        this.avatarUploading = false;
        input.value = '';
        this.showToast(this.getAvatarUploadErrorMessage(err), true);
      },
    });
  }

  onSave(): void {
    if (!this.user) return;
    this.profileSubmitAttempted = true;
    if (this.avatarUploading) {
      this.showToast('Please wait for the image upload to finish.', true);
      return;
    }

    this.profileValidationErrors = this.validateProfileInputs();
    if (this.profileValidationErrors.length) {
      this.showToast(this.profileValidationErrors[0], true);
      return;
    }

    const payload = this.buildUpdatePayload();
    if (!Object.keys(payload).length) {
      this.showToast('No changes to save.');
      return;
    }

    this.saving = true;

    if (this.user.role !== 'ENTERPRISE_USER') {
      delete payload.companyName;
    }

    this.userService.updateUser(this.user.userId, payload).subscribe({
      next: (updated: UserProfile) => {
        this.user = { ...this.user!, ...updated };
        this.resetEditModelFromUser(this.user);
        this.profileSubmitAttempted = false;
        this.profileValidationErrors = [];
        this.profileFieldErrors = {};
        this.saving = false;
        this.showToast('Profile updated successfully!');
      },
      error: (err: HttpErrorResponse) => {
        this.saving = false;
        this.showToast(this.getSaveErrorMessage(err), true);
      },
    });
  }

  onChangePassword(): void {
    if (!this.user || this.changingPassword) {
      return;
    }

    const { newPwd, confirmPwd } = this.passwordModel;
    this.passwordValidationError = '';
    if (!newPwd || !confirmPwd) {
      this.passwordValidationError = 'Please enter and confirm your new password.';
      return this.showToast(this.passwordValidationError, true);
    }
    if (newPwd !== confirmPwd) {
      this.passwordValidationError = 'New passwords do not match.';
      return this.showToast(this.passwordValidationError, true);
    }
    if (!ProfileComponent.PASSWORD_POLICY_PATTERN.test(newPwd)) {
      this.passwordValidationError =
        'Password must be at least 8 characters and include uppercase, lowercase, and a number.';
      return this.showToast(this.passwordValidationError, true);
    }

    this.changingPassword = true;
    this.userService.changePassword(this.user.userId, newPwd, confirmPwd).subscribe({
      next: () => {
        this.changingPassword = false;
        this.passwordModel = { newPwd: '', confirmPwd: '' };
        this.passwordValidationError = '';
        this.showToast('Password updated successfully!');
      },
      error: (err: HttpErrorResponse) => {
        this.changingPassword = false;
        const message = this.getPasswordChangeErrorMessage(err);
        this.passwordValidationError = message;
        this.showToast(message, true);
      },
    });
  }

  onDeleteAccount(): void {
    if (!confirm('Are you absolutely sure you want to delete your account? This cannot be undone.')) return;
    if (!this.user) return;

    this.userService.deleteUser(this.user.userId).subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.showToast('Could not delete account. Please contact support.', true),
    });
  }

  openFaceBiometricDialog(): void {
    this.faceDialogOpen = true;
  }

  closeFaceBiometricDialog(): void {
    if (this.faceBiometricSaving) {
      return;
    }
    this.faceDialogOpen = false;
  }

  onFaceBiometricCaptured(image: Blob): void {
    if (!this.user || this.faceBiometricSaving) {
      return;
    }

    this.faceBiometricSaving = true;
    this.userService.registerFaceBiometric(this.user.userId, image).subscribe({
      next: (updated) => {
        this.faceBiometricSaving = false;
        this.faceDialogOpen = false;
        this.user = { ...this.user!, ...updated };
        this.showToast(
          updated.faceBiometricRegistered
            ? 'Face login is ready for your account.'
            : 'Face login enrollment did not complete.'
        );
      },
      error: (err: HttpErrorResponse) => {
        this.faceBiometricSaving = false;
        this.showToast(this.getFaceBiometricErrorMessage(err), true);
      },
    });
  }

  onRemoveFaceBiometric(): void {
    if (!this.user || this.faceBiometricSaving || !this.user.faceBiometricRegistered) {
      return;
    }
    if (!confirm('Remove the enrolled face login from this account?')) {
      return;
    }

    this.faceBiometricSaving = true;
    this.userService.removeFaceBiometric(this.user.userId).subscribe({
      next: (updated) => {
        this.faceBiometricSaving = false;
        this.user = { ...this.user!, ...updated };
        this.showToast('Face login was removed from your account.');
      },
      error: (err: HttpErrorResponse) => {
        this.faceBiometricSaving = false;
        this.showToast(this.getFaceBiometricErrorMessage(err), true);
      },
    });
  }

  onDateOfBirthChange(value: string | null | undefined): void {
    if (!value) return;

    const dob = new Date(`${value}T00:00:00`);
    if (Number.isNaN(dob.getTime())) return;

    const today = new Date();
    let age = today.getFullYear() - dob.getFullYear();
    const beforeBirthday =
      today.getMonth() < dob.getMonth() ||
      (today.getMonth() === dob.getMonth() && today.getDate() < dob.getDate());

    if (beforeBirthday) {
      age -= 1;
    }

    this.editModel.age = age >= 0 ? age : undefined;
  }

  resetFormFields(): void {
    if (!this.user) return;
    this.resetEditModelFromUser(this.user);
    this.profileSubmitAttempted = false;
    this.profileValidationErrors = [];
    this.profileFieldErrors = {};
    this.showToast('Unsaved changes were reset.');
  }

  onProfileFormChanged(): void {
    if (!this.profileSubmitAttempted) {
      return;
    }
    this.profileValidationErrors = this.validateProfileInputs();
  }

  isUserRestricted(): boolean {
    if (!this.user) return false;

    const status = (this.user.status ?? '').toUpperCase();
    if (status === 'SUSPENDED' || status === 'BANNED') {
      return true;
    }

    const lockedUntilMs = this.parseDateMs(this.user.lockedUntil);
    if (lockedUntilMs > Date.now()) {
      return true;
    }

    return !!(this.user.reasonForBan || this.user.lockedUntil);
  }

  hasPendingAppeal(): boolean {
    return this.appeals.some((appeal) => (appeal.appealStatus ?? '').toUpperCase() === 'PENDING');
  }

  canSubmitAppeal(): boolean {
    return this.isUserRestricted() && !this.hasPendingAppeal() && !this.appealSubmitting;
  }

  onSubmitBanAppeal(): void {
    if (!this.user) return;
    this.appealValidationError = '';
    if (!this.isUserRestricted()) {
      this.showToast('Ban appeals are only available while your account is restricted.', true);
      return;
    }

    const description = this.appealDescription.trim();
    if (!description) {
      this.appealValidationError = 'Please write a short appeal before submitting.';
      this.showToast(this.appealValidationError, true);
      return;
    }
    if (description.length < 10) {
      this.appealValidationError = 'Appeal description must be at least 10 characters.';
      this.showToast(this.appealValidationError, true);
      return;
    }
    if (description.length > 1000) {
      this.appealValidationError = 'Appeal description must be 1000 characters or less.';
      this.showToast(this.appealValidationError, true);
      return;
    }

    if (this.hasPendingAppeal()) {
      this.showToast('You already have a pending appeal.', true);
      return;
    }

    this.appealSubmitting = true;
    this.http
      .post<ProfileBanAppeal>(this.appealsApiBase, { description })
      .subscribe({
        next: (appeal) => {
          this.appealSubmitting = false;
          this.appealDescription = '';
          this.appealValidationError = '';
          this.appeals = this.sortAppeals([appeal, ...this.appeals.filter((a) => a.appealId !== appeal.appealId)]);
          this.showToast('Ban appeal submitted successfully.');
        },
        error: (err: HttpErrorResponse) => {
          this.appealSubmitting = false;
          this.showToast(this.getAppealErrorMessage(err), true);
        },
      });
  }

  private resetEditModelFromUser(data: UserProfile): void {
    this.editModel = {
      firstName: data.firstName ?? '',
      lastName: data.lastName ?? '',
      username: data.username ?? '',
      profilePicture: data.profilePicture ?? '',
      age: data.age ?? undefined,
      address: data.address ?? '',
      city: data.city ?? '',
      country: data.country ?? '',
      sex: data.sex ?? '',
      companyName: data.companyName ?? '',
      dateOfBirth: data.dateOfBirth ?? '',
    };
  }

  private validateProfileInputs(): string[] {
    const errors: string[] = [];
    const fieldErrors: Partial<Record<ProfileFieldKey, string[]>> = {};
    const pushFieldError = (field: ProfileFieldKey, message: string): void => {
      const bucket = fieldErrors[field] ?? [];
      if (!bucket.includes(message)) {
        bucket.push(message);
      }
      fieldErrors[field] = bucket;
      if (!errors.includes(message)) {
        errors.push(message);
      }
    };

    const username = String(this.editModel.username ?? '').trim();
    const firstName = String(this.editModel.firstName ?? '').trim();
    const lastName = String(this.editModel.lastName ?? '').trim();
    const address = String(this.editModel.address ?? '').trim();
    const city = String(this.editModel.city ?? '').trim();
    const country = String(this.editModel.country ?? '').trim();
    const profilePicture = String(this.editModel.profilePicture ?? '').trim();
    const companyName = String(this.editModel.companyName ?? '').trim();
    const dob = String(this.editModel.dateOfBirth ?? '').trim();
    const sex = String(this.editModel.sex ?? '').trim();
    const ageValue = this.editModel.age;

    if (!username) {
      pushFieldError('username', 'Username is required.');
    } else if (!ProfileComponent.USERNAME_PATTERN.test(username)) {
      pushFieldError(
        'username',
        'Username must be 3-30 characters and use only letters, numbers, dot, underscore, or hyphen.'
      );
    }

    if (firstName && !ProfileComponent.NAME_PATTERN.test(firstName)) {
      pushFieldError('firstName', 'First name must be 1-60 letters (spaces, apostrophes, and hyphens allowed).');
    }
    if (lastName && !ProfileComponent.NAME_PATTERN.test(lastName)) {
      pushFieldError('lastName', 'Last name must be 1-60 letters (spaces, apostrophes, and hyphens allowed).');
    }

    if (address.length > 180) {
      pushFieldError('address', 'Address must be 180 characters or less.');
    }
    if (address.includes('\n') || address.includes('\r')) {
      pushFieldError('address', 'Address must be a single line.');
    }
    if (city.length > 80) {
      pushFieldError('city', 'City must be 80 characters or less.');
    }
    if (country.length > 80) {
      pushFieldError('country', 'Country must be 80 characters or less.');
    }

    if (sex && !['Male', 'Female', 'Other', 'Prefer not to say'].includes(sex)) {
      pushFieldError('sex', 'Please choose a valid sex option.');
    }

    if (typeof ageValue === 'number' && Number.isFinite(ageValue)) {
      const age = Math.trunc(ageValue);
      if (age < 0 || age > 120) {
        pushFieldError('age', 'Age must be between 0 and 120.');
      }
      if (age !== ageValue) {
        pushFieldError('age', 'Age must be a whole number.');
      }
    }

    let computedAgeFromDob: number | null = null;
    if (dob) {
      const dobDate = new Date(`${dob}T00:00:00`);
      if (Number.isNaN(dobDate.getTime())) {
        pushFieldError('dateOfBirth', 'Date of birth is invalid.');
      } else if (dobDate.getTime() > Date.now()) {
        pushFieldError('dateOfBirth', 'Date of birth cannot be in the future.');
      } else {
        const today = new Date();
        let computedAge = today.getFullYear() - dobDate.getFullYear();
        const beforeBirthday =
          today.getMonth() < dobDate.getMonth() ||
          (today.getMonth() === dobDate.getMonth() && today.getDate() < dobDate.getDate());
        if (beforeBirthday) {
          computedAge -= 1;
        }
        computedAgeFromDob = computedAge;
        if (computedAge < 0 || computedAge > 120) {
          pushFieldError('dateOfBirth', 'Date of birth produces an invalid age (must be between 0 and 120).');
        }
      }
    }

    if (
      computedAgeFromDob !== null &&
      typeof ageValue === 'number' &&
      Number.isFinite(ageValue) &&
      Math.trunc(ageValue) !== computedAgeFromDob
    ) {
      pushFieldError('age', 'Age does not match the selected date of birth.');
    }

    if (profilePicture.length > 2048) {
      pushFieldError('profilePicture', 'Profile picture URL must be 2048 characters or less.');
    }
    if (profilePicture && !this.isAcceptedProfilePictureReference(profilePicture)) {
      pushFieldError('profilePicture', 'Profile picture must be a valid http(s) URL or an uploaded image URL.');
    }

    if (this.user?.role === 'ENTERPRISE_USER') {
      if (companyName.length < 2 || companyName.length > 120) {
        pushFieldError('companyName', 'Company name must be between 2 and 120 characters for enterprise users.');
      }
    } else if (companyName.length > 120) {
      pushFieldError('companyName', 'Company name must be 120 characters or less.');
    }

    this.profileFieldErrors = fieldErrors;
    return errors;
  }

  hasProfileFieldError(field: ProfileFieldKey): boolean {
    return this.profileSubmitAttempted && !!this.profileFieldErrors[field]?.length;
  }

  getProfileFieldError(field: ProfileFieldKey): string {
    return this.profileFieldErrors[field]?.[0] ?? '';
  }

  private isAcceptedProfilePictureReference(value: string): boolean {
    if (!value) return true;
    if (value.startsWith('/images/')) return true;

    try {
      const parsed = new URL(value);
      return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
      return false;
    }
  }

  private loadModerationPanels(userId: number): void {
    this.loadWarnings(userId);
    this.loadAppeals(userId);
  }

  private loadWarnings(userId: number): void {
    this.warningsLoading = true;
    this.warningsLoadFailed = false;

    this.http.get<ProfileWarning[]>(`${this.warningsApiBase}/user/${userId}`).subscribe({
      next: (warnings) => {
        this.warnings = this.sortWarnings(warnings ?? []);
        this.warningsLoading = false;
      },
      error: () => {
        this.warnings = [];
        this.warningsLoading = false;
        this.warningsLoadFailed = true;
      },
    });
  }

  private loadAppeals(userId: number): void {
    this.appealsLoading = true;
    this.appealsLoadFailed = false;

    this.http.get<ProfileBanAppeal[]>(`${this.appealsApiBase}/user/${userId}`).subscribe({
      next: (appeals) => {
        this.appeals = this.sortAppeals(appeals ?? []);
        this.appealsLoading = false;
      },
      error: () => {
        this.appeals = [];
        this.appealsLoading = false;
        this.appealsLoadFailed = true;
      },
    });
  }

  private buildUpdatePayload(): Partial<UserProfile> {
    if (!this.user) return {};

    const raw: Partial<UserProfile> = { ...this.editModel };
    const current = this.user;
    const payload: Partial<UserProfile> = {};
    const payloadAny = payload as Record<string, unknown>;

    const stringFields: Array<keyof UserProfile> = [
      'firstName',
      'lastName',
      'username',
      'profilePicture',
      'address',
      'city',
      'country',
      'sex',
      'companyName',
    ];

    for (const key of stringFields) {
      if (!(key in raw)) continue;
      const value = raw[key];
      if (typeof value === 'string') {
        const normalized = value.trim();
        const nextValue = normalized === '' ? null : normalized;
        const currentValue = typeof current[key] === 'string'
          ? (current[key] as string).trim() || null
          : (current[key] ?? null);
        if (nextValue !== currentValue) {
          payloadAny[String(key)] = nextValue;
        }
      } else if (value === null && current[key] != null) {
        payloadAny[String(key)] = null;
      }
    }

    const dobRaw = raw.dateOfBirth;
    if (typeof dobRaw === 'string') {
      const trimmedDob = dobRaw.trim();
      const currentDob = (current.dateOfBirth ?? '').trim();
      // Omit blank DOB instead of sending "", which can fail depending on server parsing settings.
      if (trimmedDob && trimmedDob !== currentDob) {
        payload.dateOfBirth = trimmedDob;
      }
    } else if (dobRaw === null && current.dateOfBirth != null) {
      payload.dateOfBirth = null;
    }

    const ageRaw = raw.age;
    if (typeof ageRaw === 'number' && Number.isFinite(ageRaw)) {
      const nextAge = Math.trunc(ageRaw);
      const currentAge = current.age ?? null;
      if (nextAge > 0 && nextAge !== currentAge) {
        payload.age = nextAge;
      }
    }

    return payload;
  }

  private showToast(message: string, isError = false): void {
    this.toastMessage = message;
    this.toastError = isError;
    this.toastVisible = true;
    setTimeout(() => (this.toastVisible = false), 3500);
  }

  private getSaveErrorMessage(err: HttpErrorResponse): string {
    const backendMessage =
      typeof err?.error === 'string'
        ? err.error
        : typeof err?.error?.message === 'string'
          ? err.error.message
          : null;

    if (err?.status === 0) {
      return 'Save failed: could not reach the server.';
    }
    if (err?.status === 403) {
      return 'Save failed: you are not allowed to edit this profile.';
    }
    if (err?.status === 400) {
      return backendMessage ? `Save failed: ${backendMessage}` : 'Save failed: invalid profile data.';
    }
    return backendMessage ? `Save failed: ${backendMessage}` : 'Failed to save changes. Please try again.';
  }

  private getAvatarUploadErrorMessage(err: HttpErrorResponse): string {
    const backendMessage =
      typeof err?.error === 'string'
        ? err.error
        : typeof err?.error?.message === 'string'
          ? err.error.message
          : null;

    if (err?.status === 0) {
      return 'Image upload failed: could not reach the server.';
    }
    if (err?.status === 413) {
      return 'Image upload failed: file is too large.';
    }
    if (err?.status === 400) {
      return backendMessage ? `Image upload failed: ${backendMessage}` : 'Image upload failed: invalid image file.';
    }

    return backendMessage ? `Image upload failed: ${backendMessage}` : 'Image upload failed. Please try again.';
  }

  private getAppealErrorMessage(err: HttpErrorResponse): string {
    const backendMessage =
      typeof err?.error === 'string'
        ? err.error
        : typeof err?.error?.message === 'string'
          ? err.error.message
          : null;

    if (err?.status === 0) {
      return 'Appeal failed: could not reach the server.';
    }
    if (err?.status === 403) {
      return 'Appeal failed: you are not allowed to submit an appeal for this account.';
    }
    if (err?.status === 400) {
      return backendMessage ? `Appeal failed: ${backendMessage}` : 'Appeal failed: invalid request.';
    }

    return backendMessage ? `Appeal failed: ${backendMessage}` : 'Could not submit ban appeal. Please try again.';
  }

  private getFaceBiometricErrorMessage(err: HttpErrorResponse): string {
    const backendMessage =
      typeof err?.error === 'string'
        ? err.error
        : typeof err?.error?.message === 'string'
          ? err.error.message
          : null;

    if (err?.status === 0) {
      return 'Face login failed: could not reach the server.';
    }
    if (err?.status === 403) {
      return 'Face login failed: you are not allowed to update this biometric.';
    }
    if (err?.status === 400) {
      return backendMessage ? `Face login failed: ${backendMessage}` : 'Face login failed: invalid image.';
    }

    return backendMessage ? `Face login failed: ${backendMessage}` : 'Face login failed. Please try again.';
  }

  private getPasswordChangeErrorMessage(err: HttpErrorResponse): string {
    const backendMessage =
      typeof err?.error === 'string'
        ? err.error
        : typeof err?.error?.message === 'string'
          ? err.error.message
          : null;

    if (err?.status === 0) {
      return 'Password update failed: could not reach the server.';
    }
    if (err?.status === 403) {
      return 'Password update failed: you are not allowed to change this password.';
    }
    if (err?.status === 400) {
      return backendMessage ? `Password update failed: ${backendMessage}` : 'Password update failed: invalid request.';
    }

    return backendMessage ? `Password update failed: ${backendMessage}` : 'Password update failed. Please try again.';
  }

  private sortWarnings(warnings: ProfileWarning[]): ProfileWarning[] {
    return [...warnings].sort((a, b) => this.parseDateMs(b.issuedDate) - this.parseDateMs(a.issuedDate));
  }

  private sortAppeals(appeals: ProfileBanAppeal[]): ProfileBanAppeal[] {
    return [...appeals].sort((a, b) => this.parseDateMs(b.submittedDate) - this.parseDateMs(a.submittedDate));
  }

  private parseDateMs(value: string | null | undefined): number {
    if (!value) return 0;
    const ms = Date.parse(value);
    return Number.isFinite(ms) ? ms : 0;
  }
}
