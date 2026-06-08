import { Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, finalize, forkJoin } from 'rxjs';
import { environment } from '../../../environments/environment';
import { getDisplayName, logout } from '../../auth/keycloak.service';

type AdminFilter =
  | 'all'
  | 'pending_verification'
  | 'banned'
  | 'warned'
  | 'instructor'
  | 'enterprise'
  | 'student'
  | 'admin';

type Role = 'ADMIN' | 'INSTRUCTOR' | 'STUDENT' | 'ENTERPRISE_USER' | string;
type IntegrityStatus = 'SECURE' | 'WARNED' | 'SUSPENDED' | 'FLAGGED' | string;
type AppealQueueFilter = 'all' | 'pending' | 'unviewed' | 'resolved';

interface AdminUserOverview {
  userId: number;
  keycloakUserId?: string | null;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  email: string;
  role: Role;
  profilePicture?: string | null;
  companyName?: string | null;
  city?: string | null;
  country?: string | null;
  status?: IntegrityStatus | null;
  adminVerified: boolean;
  enterpriseVerified: boolean;
  verificationRequired: boolean;
  verificationApproved: boolean;
  banned: boolean;
  reasonForBan?: string | null;
  banPeriod?: number | null;
  lockedUntil?: string | null;
  streakDays: number;
  lastLogin?: string | null;
  accountCreatedAt?: string | null;
  lastProfileUpdate?: string | null;
  warningCount: number;
  activityCountLast30Days: number;
  latestActivityAt?: string | null;
}

interface AdminUserDetail {
  userId: number;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  email: string;
  role: Role;
  dateOfBirth?: string | null;
  profilePicture?: string | null;
  age?: number | null;
  address?: string | null;
  city?: string | null;
  country?: string | null;
  sex?: string | null;
  companyName?: string | null;
  adminVerified: boolean;
  adminVerifiedAt?: string | null;
  adminVerifiedBy?: string | null;
  enterpriseVerified: boolean;
  enterpriseOnboardedAt?: string | null;
  streakDays: number;
  lastLogin?: string | null;
  accountCreatedAt?: string | null;
  lastProfileUpdate?: string | null;
  lockedUntil?: string | null;
  reasonForBan?: string | null;
  banPeriod?: number | null;
  status?: IntegrityStatus | null;
}

interface UserActivity {
  id: number;
  eventType: string;
  route?: string | null;
  durationMs?: number | null;
  occurredAtEpochMs?: number | null;
  receivedAt?: string | null;
  role?: string | null;
}

interface WarningRecord {
  warningId: number;
  reason: string;
  severity: string;
  issuedDate?: string | null;
  issuedBy?: string | null;
  userId: number;
}

interface BanAppealRecord {
  appealId: number;
  description: string;
  appealStatus?: string | null;
  submittedDate?: string | null;
  resolvedDate?: string | null;
  reviewedBy?: string | null;
  viewed: boolean;
  viewedAt?: string | null;
  viewedBy?: string | null;
  userId: number;
}

interface AdminUserOverviewPageResponse {
  content?: unknown;
  totalElements?: unknown;
  totalPages?: unknown;
  page?: unknown;
  size?: unknown;
  pendingVerificationCount?: unknown;
  bannedCount?: unknown;
  warnedCount?: unknown;
  activeRecentlyCount?: unknown;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css',
  host: { style: 'display:block' },
})
export class UsersComponent {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  private readonly usersApi = `${environment.apiBaseUrl}/api/users`;
  private readonly warningsApi = `${environment.apiBaseUrl}/api/warnings`;
  private readonly appealsApi = `${environment.apiBaseUrl}/api/ban-appeals`;

  readonly searchForm = this.fb.nonNullable.group({
    search: ['', [Validators.maxLength(120)]],
  });

  readonly banForm = this.fb.nonNullable.group({
    reasonForBan: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(500)]],
    banPeriodDays: [7, [Validators.required, Validators.min(1), Validators.max(3650)]],
  });

  readonly warningForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    severity: ['MEDIUM', [Validators.required]],
  });

  readonly editWarningForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    severity: ['MEDIUM', [Validators.required]],
  });

  readonly warningSeverities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

  readonly filters: Array<{ key: AdminFilter; label: string }> = [
    { key: 'pending_verification', label: 'Pending Verification' },
    { key: 'banned', label: 'Banned' },
    { key: 'warned', label: 'Warned' },
    { key: 'instructor', label: 'Teachers' },
    { key: 'enterprise', label: 'Enterprise' },
    { key: 'student', label: 'Students' },
    { key: 'admin', label: 'Admins' },
    { key: 'all', label: 'All Users' },
  ];

  activeFilter: AdminFilter = 'pending_verification';
  users: AdminUserOverview[] = [];
  readonly userPageSizeOptions = [10, 20, 50] as const;
  userPage = 1;
  userPageSize = 20;
  totalUsers = 0;
  totalUserPages = 1;
  totalPendingVerification = 0;
  totalBannedUsers = 0;
  totalWarnedUsers = 0;
  totalActiveRecently = 0;
  selectedUserId: number | null = null;
  selectedOverview: AdminUserOverview | null = null;
  selectedUser: AdminUserDetail | null = null;
  activities: UserActivity[] = [];
  warnings: WarningRecord[] = [];
  appeals: BanAppealRecord[] = [];
  appealQueue: BanAppealRecord[] = [];

  loadingList = false;
  loadingDetails = false;
  loadingAppealQueue = false;
  savingModeration = false;
  savingWarning = false;
  deletingWarningId: number | null = null;
  editingWarningId: number | null = null;
  savingEditWarning = false;
  editWarningFormSubmitted = false;
  error = '';
  detailError = '';
  queueError = '';
  successMessage = '';
  adminDisplayName = 'Admin';
  banFormSubmitted = false;
  warningFormSubmitted = false;
  processingAppealId: number | null = null;
  appealQueueFilter: AppealQueueFilter = 'unviewed';
  private hasLoadedAppealQueue = false;

  ngOnInit(): void {
    this.adminDisplayName = getDisplayName() ?? 'Admin';
    this.loadUsers();

    this.searchForm.controls.search.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.userPage = 1;
        this.loadUsers();
      });
  }

  setFilter(filter: AdminFilter): void {
    if (this.activeFilter === filter) return;
    this.activeFilter = filter;
    this.userPage = 1;
    this.loadUsers();
  }

  loadUsers(): void {
    this.loadingList = true;
    this.error = '';
    const search = this.searchForm.getRawValue().search.trim();
    const params = new URLSearchParams();
    if (search) params.set('search', search);
    params.set('filter', this.activeFilter);
    params.set('page', String(this.userPage - 1));
    params.set('size', String(this.userPageSize));

    this.http
      .get<unknown>(`${this.usersApi}/admin/overview?${params.toString()}`)
      .pipe(finalize(() => (this.loadingList = false)))
      .subscribe({
        next: (payload) => {
          this.applyOverviewPayload(payload);
          if (!this.hasLoadedAppealQueue || !!this.queueError) {
            this.hasLoadedAppealQueue = true;
            this.loadAppealQueue();
          }
          this.refreshSelectionAfterListReload();
          if (!this.selectedUserId && this.users.length) {
            this.selectUser(this.users[0]);
          }
        },
        error: (err) => {
          this.error = String(err?.error?.message ?? err?.message ?? 'Failed to load users');
        },
      });
  }

  selectUser(user: AdminUserOverview): void {
    if (!user?.userId) return;
    this.selectedUserId = user.userId;
    this.selectedOverview = user;
    this.loadSelectedUserData(user.userId);
  }

  pagedUsers(): AdminUserOverview[] {
    return this.users;
  }

  userTotalPages(): number {
    return Math.max(1, this.totalUserPages);
  }

  userPageStartIndex(): number {
    if (this.totalUsers === 0) return 0;
    return (this.userPage - 1) * this.userPageSize + 1;
  }

  userPageEndIndex(): number {
    const endByPage = (this.userPage - 1) * this.userPageSize + this.users.length;
    return Math.min(this.totalUsers, endByPage);
  }

  canGoToPreviousUserPage(): boolean {
    return this.userPage > 1;
  }

  canGoToNextUserPage(): boolean {
    return this.userPage < this.userTotalPages();
  }

  goToPreviousUserPage(): void {
    if (!this.canGoToPreviousUserPage()) return;
    this.userPage -= 1;
    this.loadUsers();
  }

  goToNextUserPage(): void {
    if (!this.canGoToNextUserPage()) return;
    this.userPage += 1;
    this.loadUsers();
  }

  setUserPageSize(size: number): void {
    const nextSize = Number(size);
    if (!Number.isFinite(nextSize) || nextSize < 1) return;
    if (!this.userPageSizeOptions.some((option) => option === nextSize)) return;
    if (this.userPageSize === nextSize) return;

    this.userPageSize = nextSize;
    this.userPage = 1;
    this.loadUsers();
  }

  toggleVerification(): void {
    if (!this.selectedUser || !this.requiresVerification(this.selectedUser) || this.savingModeration) {
      return;
    }
    const next = !this.verificationApproved(this.selectedUser);
    this.savingModeration = true;
    this.detailError = '';

    this.http
      .put<AdminUserDetail>(`${this.usersApi}/${this.selectedUser.userId}/admin-verification`, { verified: next })
      .pipe(finalize(() => (this.savingModeration = false)))
      .subscribe({
        next: (updated) => {
          this.selectedUser = updated;
          this.successMessage = next ? 'User verification approved.' : 'User verification revoked.';
          this.patchOverviewFromDetail(updated);
          this.reloadListSilently();
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? 'Failed to update verification');
        },
      });
  }

  banSelectedUser(): void {
    this.banFormSubmitted = true;
    this.detailError = '';
    if (!this.selectedUser || this.banForm.invalid || this.savingModeration) {
      this.banForm.markAllAsTouched();
      return;
    }
    const { reasonForBan, banPeriodDays } = this.banForm.getRawValue();
    const trimmedReason = reasonForBan.trim();
    if (trimmedReason.length < 5 || trimmedReason.length > 500) {
      this.detailError = 'Ban reason must be between 5 and 500 characters.';
      this.banForm.controls.reasonForBan.markAsTouched();
      return;
    }
    this.updateBanState(true, trimmedReason, Number(banPeriodDays));
  }

  unbanSelectedUser(): void {
    if (!this.selectedUser || this.savingModeration) return;
    this.updateBanState(false, null, null);
  }

  issueWarning(): void {
    this.warningFormSubmitted = true;
    this.detailError = '';
    if (!this.selectedUser || this.warningForm.invalid || this.savingWarning) {
      this.warningForm.markAllAsTouched();
      return;
    }

    const raw = this.warningForm.getRawValue();
    const trimmedReason = raw.reason.trim();
    if (trimmedReason.length < 3 || trimmedReason.length > 500) {
      this.detailError = 'Warning reason must be between 3 and 500 characters.';
      this.warningForm.controls.reason.markAsTouched();
      return;
    }
    this.savingWarning = true;
    this.detailError = '';

    this.http
      .post<WarningRecord>(this.warningsApi, {
        userId: this.selectedUser.userId,
        reason: trimmedReason,
        severity: raw.severity,
      })
      .pipe(finalize(() => (this.savingWarning = false)))
      .subscribe({
        next: (created) => {
          this.warnings = [created, ...this.warnings];
          this.warningForm.reset({ reason: '', severity: 'MEDIUM' });
          this.warningFormSubmitted = false;
          this.successMessage = 'Warning issued successfully.';
          this.bumpWarningCount(1);
          this.reloadListSilently();
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? 'Failed to issue warning');
        },
      });
  }

  deleteWarning(warning: WarningRecord): void {
    if (!warning?.warningId || this.deletingWarningId != null) return;
    this.deletingWarningId = warning.warningId;
    this.detailError = '';

    this.http
      .delete<void>(`${this.warningsApi}/${warning.warningId}`)
      .pipe(finalize(() => (this.deletingWarningId = null)))
      .subscribe({
        next: () => {
          this.warnings = this.warnings.filter((w) => w.warningId !== warning.warningId);
          this.successMessage = 'Warning removed.';
          this.bumpWarningCount(-1);
          this.reloadListSilently();
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? 'Failed to delete warning');
        },
      });
  }

  isDeletingWarning(warning: WarningRecord): boolean {
    return this.deletingWarningId === warning.warningId;
  }

  isEditingWarning(warning: WarningRecord): boolean {
    return this.editingWarningId === warning.warningId;
  }

  editWarning(warning: WarningRecord): void {
    if (this.deletingWarningId != null || this.savingEditWarning) return;
    this.editingWarningId = warning.warningId;
    this.editWarningFormSubmitted = false;
    this.editWarningForm.reset({ reason: warning.reason, severity: warning.severity });
  }

  cancelEditWarning(): void {
    this.editingWarningId = null;
    this.editWarningFormSubmitted = false;
  }

  saveEditWarning(warning: WarningRecord): void {
    this.editWarningFormSubmitted = true;
    if (this.editWarningForm.invalid || this.savingEditWarning) {
      this.editWarningForm.markAllAsTouched();
      return;
    }
    const raw = this.editWarningForm.getRawValue();
    const trimmedReason = raw.reason.trim();
    if (trimmedReason.length < 3 || trimmedReason.length > 500) {
      this.editWarningForm.controls.reason.markAsTouched();
      return;
    }
    this.savingEditWarning = true;
    this.detailError = '';

    this.http
      .put<WarningRecord>(`${this.warningsApi}/${warning.warningId}`, {
        userId: warning.userId,
        reason: trimmedReason,
        severity: raw.severity,
      })
      .pipe(finalize(() => (this.savingEditWarning = false)))
      .subscribe({
        next: (updated) => {
          this.warnings = this.warnings.map((w) => (w.warningId === updated.warningId ? updated : w));
          this.editingWarningId = null;
          this.editWarningFormSubmitted = false;
          this.successMessage = 'Warning updated.';
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? 'Failed to update warning');
        },
      });
  }

  showEditWarningReasonError(): boolean {
    const control = this.editWarningForm.controls.reason;
    return (this.editWarningFormSubmitted || control.touched || control.dirty) && control.invalid;
  }

  isSelected(user: AdminUserOverview): boolean {
    return this.selectedUserId === user.userId;
  }

  requiresVerification(user: { role: Role } | null): boolean {
    if (!user) return false;
    return user.role === 'INSTRUCTOR' || user.role === 'ENTERPRISE_USER';
  }

  verificationApproved(user: Pick<AdminUserDetail, 'role' | 'adminVerified' | 'enterpriseVerified'> | null): boolean {
    if (!user) return false;
    return user.role === 'ENTERPRISE_USER' ? !!user.enterpriseVerified : !!user.adminVerified;
  }

  isEnterpriseUser(user: { role: Role } | null): boolean {
    return !!user && user.role === 'ENTERPRISE_USER';
  }

  isBanned(user: { lockedUntil?: string | null } | null): boolean {
    if (!user) return false;
    const userAny = user as { status?: IntegrityStatus | null; reasonForBan?: string | null };
    const status = String(userAny.status ?? '').toUpperCase();
    if (status === 'SUSPENDED' || status === 'BANNED') {
      return true;
    }
    if (userAny.reasonForBan) {
      return true;
    }
    if (!user.lockedUntil) return false;
    const dt = new Date(user.lockedUntil);
    return !Number.isNaN(dt.getTime()) && dt.getTime() > Date.now();
  }

  getUserDisplayName(user: { firstName?: string | null; lastName?: string | null; username: string } | null): string {
    if (!user) return '';
    const name = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    return name || user.username;
  }

  getInitials(user: { firstName?: string | null; lastName?: string | null; username: string } | null): string {
    if (!user) return '?';
    const first = user.firstName?.trim()?.[0] ?? '';
    const last = user.lastName?.trim()?.[0] ?? '';
    const initials = (first + last).toUpperCase();
    return initials || user.username?.[0]?.toUpperCase() || '?';
  }

  getStatusLabel(status: IntegrityStatus | null | undefined): string {
    return String(status ?? 'SECURE');
  }

  warningSeverityClass(severity: string | null | undefined): string {
    const normalized = String(severity ?? '').toLowerCase();
    if (normalized === 'low') return 'sev-low';
    if (normalized === 'high') return 'sev-high';
    if (normalized === 'critical') return 'sev-critical';
    return 'sev-medium';
  }

  trackUser(_: number, user: AdminUserOverview): number {
    return user.userId;
  }

  trackActivity(_: number, activity: UserActivity): number {
    return activity.id;
  }

  trackWarning(_: number, warning: WarningRecord): number {
    return warning.warningId;
  }

  trackAppeal(_: number, appeal: BanAppealRecord): number {
    return appeal.appealId;
  }

  isAppealPending(appeal: BanAppealRecord | null | undefined): boolean {
    return String(appeal?.appealStatus ?? '').toUpperCase() === 'PENDING';
  }

  isAppealViewed(appeal: BanAppealRecord | null | undefined): boolean {
    return !!appeal?.viewed;
  }

  setAppealQueueFilter(filter: AppealQueueFilter): void {
    this.appealQueueFilter = filter;
  }

  refreshAppealQueue(): void {
    if (this.loadingAppealQueue) return;
    this.loadAppealQueue();
  }

  appealQueueItems(): BanAppealRecord[] {
    if (this.appealQueueFilter === 'pending') {
      return this.appealQueue.filter((appeal) => this.isAppealPending(appeal));
    }
    if (this.appealQueueFilter === 'unviewed') {
      return this.appealQueue.filter((appeal) => !appeal.viewed);
    }
    if (this.appealQueueFilter === 'resolved') {
      return this.appealQueue.filter((appeal) => !this.isAppealPending(appeal));
    }
    return this.appealQueue;
  }

  pendingAppealCount(): number {
    return this.appealQueue.filter((appeal) => this.isAppealPending(appeal)).length;
  }

  unviewedAppealCount(): number {
    return this.appealQueue.filter((appeal) => !appeal.viewed).length;
  }

  resolvedAppealCount(): number {
    return this.appealQueue.filter((appeal) => !this.isAppealPending(appeal)).length;
  }

  appealUserLabel(appeal: BanAppealRecord): string {
    const user = this.users.find((item) => item.userId === appeal.userId) ?? null;
    if (user) {
      return `${this.getUserDisplayName(user)} (${user.email})`;
    }
    return `User #${appeal.userId}`;
  }

  isProcessingAppeal(appeal: BanAppealRecord | null | undefined): boolean {
    return !!appeal && this.processingAppealId === appeal.appealId;
  }

  approveAppeal(appeal: BanAppealRecord): void {
    this.updateAppealStatus(appeal, 'approve');
  }

  rejectAppeal(appeal: BanAppealRecord): void {
    this.updateAppealStatus(appeal, 'reject');
  }

  markAppealViewed(appeal: BanAppealRecord): void {
    if (!appeal || appeal.viewed || this.processingAppealId != null) return;
    this.callAppealViewUpdate(appeal, true);
  }

  markAppealUnviewed(appeal: BanAppealRecord): void {
    if (!appeal || !appeal.viewed || this.processingAppealId != null) return;
    this.callAppealViewUpdate(appeal, false);
  }

  focusAppealUser(appeal: BanAppealRecord): void {
    if (!appeal?.userId) return;
    const existing = this.users.find((u) => u.userId === appeal.userId) ?? null;
    if (existing) {
      this.selectUser(existing);
      return;
    }

    if (this.activeFilter !== 'all') {
      this.activeFilter = 'all';
      this.userPage = 1;
      this.loadUsers();
    }
    this.selectedUserId = appeal.userId;
    this.selectedOverview = null;
    this.loadSelectedUserData(appeal.userId);
  }

  async logoutAdmin(): Promise<void> {
    await logout();
  }

  private loadSelectedUserData(userId: number): void {
    this.loadingDetails = true;
    this.detailError = '';
    this.successMessage = '';

    forkJoin({
      user: this.http.get<AdminUserDetail>(`${this.usersApi}/${userId}`),
      activities: this.http.get<UserActivity[]>(`${this.usersApi}/${userId}/activities?limit=20`),
      warnings: this.http.get<WarningRecord[]>(`${this.warningsApi}/user/${userId}`),
      appeals: this.http.get<unknown>(`${this.appealsApi}/user/${userId}`),
    })
      .pipe(finalize(() => (this.loadingDetails = false)))
      .subscribe({
        next: ({ user, activities, warnings, appeals }) => {
          this.selectedUser = user;
          this.activities = activities;
          this.warnings = [...warnings].sort((a, b) => {
            const at = new Date(a.issuedDate ?? 0).getTime();
            const bt = new Date(b.issuedDate ?? 0).getTime();
            return bt - at;
          });
          this.appeals = this.sortAppeals(this.normalizeAppealCollection(appeals));
          this.mergeAppealsIntoQueue(this.appeals);
          this.banForm.patchValue({
            reasonForBan: user.reasonForBan ?? '',
            banPeriodDays: user.banPeriod ?? 7,
          });
          this.banFormSubmitted = false;
          this.warningForm.patchValue({
            reason: '',
            severity: 'MEDIUM',
          });
          this.warningFormSubmitted = false;
        },
        error: (err) => {
          this.selectedUser = null;
          this.activities = [];
          this.warnings = [];
          this.appeals = [];
          this.detailError = String(err?.error?.message ?? err?.message ?? 'Failed to load user details');
        },
      });
  }

  private updateBanState(banned: boolean, reasonForBan: string | null, banPeriodDays: number | null): void {
    if (!this.selectedUser) return;
    this.savingModeration = true;
    this.detailError = '';

    this.http
      .put<AdminUserDetail>(`${this.usersApi}/${this.selectedUser.userId}/ban`, {
        banned,
        reasonForBan,
        banPeriodDays,
      })
      .pipe(finalize(() => (this.savingModeration = false)))
      .subscribe({
        next: (updated) => {
          this.selectedUser = updated;
          this.successMessage = banned ? 'User has been banned.' : 'User has been unbanned.';
          this.patchOverviewFromDetail(updated);
          this.reloadListSilently();
          if (!banned) {
            this.banForm.patchValue({ reasonForBan: '', banPeriodDays: 7 });
            this.banFormSubmitted = false;
          }
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? 'Failed to update ban status');
        },
      });
  }

  private refreshSelectionAfterListReload(): void {
    if (!this.selectedUserId) return;
    const match = this.users.find((u) => u.userId === this.selectedUserId) ?? null;
    this.selectedOverview = match;
  }

  private patchOverviewFromDetail(detail: AdminUserDetail): void {
    if (!detail) return;
    const idx = this.users.findIndex((u) => u.userId === detail.userId);
    if (idx < 0) return;

    const existing = this.users[idx];
    const patched: AdminUserOverview = {
      ...existing,
      username: detail.username,
      firstName: detail.firstName,
      lastName: detail.lastName,
      email: detail.email,
      role: detail.role,
      profilePicture: detail.profilePicture,
      companyName: detail.companyName,
      city: detail.city,
      country: detail.country,
      status: detail.status,
      adminVerified: detail.adminVerified,
      enterpriseVerified: detail.enterpriseVerified,
      verificationApproved: detail.role === 'ENTERPRISE_USER' ? !!detail.enterpriseVerified : !!detail.adminVerified,
      reasonForBan: detail.reasonForBan,
      banPeriod: detail.banPeriod,
      lockedUntil: detail.lockedUntil,
      lastLogin: detail.lastLogin,
      accountCreatedAt: detail.accountCreatedAt,
      lastProfileUpdate: detail.lastProfileUpdate,
      banned: this.isBanned(detail),
      streakDays: detail.streakDays,
    };
    this.users = [...this.users.slice(0, idx), patched, ...this.users.slice(idx + 1)];
    this.selectedOverview = patched;
  }

  private bumpWarningCount(delta: number): void {
    if (!this.selectedUserId) return;
    const idx = this.users.findIndex((u) => u.userId === this.selectedUserId);
    if (idx < 0) return;
    const current = this.users[idx];
    const nextCount = Math.max(0, (current.warningCount ?? 0) + delta);
    const nextStatus: IntegrityStatus =
      nextCount > 0 && current.status === 'SECURE' ? 'WARNED' : (current.status ?? 'SECURE');
    const patched: AdminUserOverview = {
      ...current,
      warningCount: nextCount,
      status: nextStatus,
    };
    this.users = [...this.users.slice(0, idx), patched, ...this.users.slice(idx + 1)];
    this.selectedOverview = patched;
  }

  private reloadListSilently(): void {
    const search = this.searchForm.getRawValue().search.trim();
    const params = new URLSearchParams();
    if (search) params.set('search', search);
    params.set('filter', this.activeFilter);
    params.set('page', String(this.userPage - 1));
    params.set('size', String(this.userPageSize));

    this.http.get<unknown>(`${this.usersApi}/admin/overview?${params.toString()}`).subscribe({
      next: (payload) => {
        this.applyOverviewPayload(payload);
        this.refreshSelectionAfterListReload();
      },
    });
  }

  pendingVerificationCount(): number {
    return this.totalPendingVerification;
  }

  bannedCount(): number {
    return this.totalBannedUsers;
  }

  activeRecentlyCount(): number {
    return this.totalActiveRecently;
  }

  showBanReasonError(): boolean {
    const control = this.banForm.controls.reasonForBan;
    return (this.banFormSubmitted || control.touched || control.dirty) && control.invalid;
  }

  showBanPeriodError(): boolean {
    const control = this.banForm.controls.banPeriodDays;
    return (this.banFormSubmitted || control.touched || control.dirty) && control.invalid;
  }

  banReasonErrorMessage(): string {
    const control = this.banForm.controls.reasonForBan;
    if (control.hasError('required')) return 'Ban reason is required.';
    if (control.hasError('minlength')) return 'Ban reason must be at least 5 characters.';
    if (control.hasError('maxlength')) return 'Ban reason must be 500 characters or less.';
    return 'Ban reason is invalid.';
  }

  banPeriodErrorMessage(): string {
    const control = this.banForm.controls.banPeriodDays;
    if (control.hasError('required')) return 'Ban period is required.';
    if (control.hasError('min')) return 'Ban period must be at least 1 day.';
    if (control.hasError('max')) return 'Ban period must be 3650 days or less.';
    return 'Ban period is invalid.';
  }

  showWarningReasonError(): boolean {
    const control = this.warningForm.controls.reason;
    return (this.warningFormSubmitted || control.touched || control.dirty) && control.invalid;
  }

  warningReasonErrorMessage(): string {
    const control = this.warningForm.controls.reason;
    if (control.hasError('required')) return 'Warning reason is required.';
    if (control.hasError('minlength')) return 'Warning reason must be at least 3 characters.';
    if (control.hasError('maxlength')) return 'Warning reason must be 500 characters or less.';
    return 'Warning reason is invalid.';
  }

  warnedCount(): number {
    return this.totalWarnedUsers;
  }

  private loadAppealQueue(): void {
    this.loadingAppealQueue = true;
    this.queueError = '';
    this.http
      .get<unknown>(this.appealsApi)
      .pipe(finalize(() => (this.loadingAppealQueue = false)))
      .subscribe({
        next: (payload) => {
          this.appealQueue = this.sortAppeals(this.normalizeAppealCollection(payload));
        },
        error: (err) => {
          this.appealQueue = [];
          this.queueError = this.resolveAppealQueueError(err);
        },
      });
  }

  private sortAppeals(appeals: BanAppealRecord[]): BanAppealRecord[] {
    return [...appeals].sort((a, b) => {
      const aPending = this.isAppealPending(a) ? 1 : 0;
      const bPending = this.isAppealPending(b) ? 1 : 0;
      if (aPending !== bPending) {
        return bPending - aPending;
      }
      const at = new Date(a.submittedDate ?? 0).getTime();
      const bt = new Date(b.submittedDate ?? 0).getTime();
      return bt - at;
    });
  }

  private mergeAppealsIntoQueue(appeals: BanAppealRecord[]): void {
    if (!appeals.length) return;
    this.queueError = '';
    const byId = new Map(this.appealQueue.map((appeal) => [appeal.appealId, appeal]));
    for (const appeal of appeals) {
      byId.set(appeal.appealId, appeal);
    }
    this.appealQueue = this.sortAppeals(Array.from(byId.values()));
  }

  private applyAppealUpdate(updated: BanAppealRecord): void {
    this.appeals = this.sortAppeals(
      this.appeals.map((item) => (item.appealId === updated.appealId ? updated : item))
    );
    const queueMatch = this.appealQueue.some((item) => item.appealId === updated.appealId);
    if (queueMatch) {
      this.appealQueue = this.sortAppeals(
        this.appealQueue.map((item) => (item.appealId === updated.appealId ? updated : item))
      );
      return;
    }
    this.appealQueue = this.sortAppeals([updated, ...this.appealQueue]);
  }

  private callAppealViewUpdate(appeal: BanAppealRecord, viewed: boolean): void {
    if (!appeal || this.processingAppealId != null) return;

    this.processingAppealId = appeal.appealId;
    this.detailError = '';
    const endpoint = viewed ? 'viewed' : 'unviewed';

    this.http
      .post<unknown>(`${this.appealsApi}/${appeal.appealId}/${endpoint}`, {})
      .pipe(finalize(() => (this.processingAppealId = null)))
      .subscribe({
        next: (payload) => {
          const updated = this.normalizeAppealRecord(payload);
          if (!updated) {
            this.detailError = 'Appeal update returned an unexpected response.';
            this.loadAppealQueue();
            return;
          }
          this.applyAppealUpdate(updated);
          this.successMessage = viewed ? 'Appeal marked as viewed.' : 'Appeal marked as unviewed.';
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? `Failed to mark appeal as ${endpoint}`);
        },
      });
  }

  private updateAppealStatus(appeal: BanAppealRecord, action: 'approve' | 'reject'): void {
    if (!appeal || !this.isAppealPending(appeal) || this.processingAppealId != null) {
      return;
    }

    this.processingAppealId = appeal.appealId;
    this.detailError = '';

    this.http
      .post<unknown>(`${this.appealsApi}/${appeal.appealId}/${action}`, {})
      .pipe(finalize(() => (this.processingAppealId = null)))
      .subscribe({
        next: (payload) => {
          const updated = this.normalizeAppealRecord(payload);
          if (!updated) {
            this.detailError = 'Appeal update returned an unexpected response.';
            this.loadAppealQueue();
            return;
          }
          this.applyAppealUpdate(updated);
          this.successMessage = action === 'approve' ? 'Appeal approved.' : 'Appeal rejected.';
        },
        error: (err) => {
          this.detailError = String(err?.error?.message ?? err?.message ?? `Failed to ${action} appeal`);
        },
      });
  }

  private normalizeAppealCollection(payload: unknown): BanAppealRecord[] {
    const rawItems = this.extractAppealItems(payload);
    return rawItems
      .map((item) => this.normalizeAppealRecord(item))
      .filter((item): item is BanAppealRecord => item !== null);
  }

  private extractAppealItems(payload: unknown): unknown[] {
    if (Array.isArray(payload)) {
      return payload;
    }
    if (!payload || typeof payload !== 'object') {
      return [];
    }
    const container = payload as Record<string, unknown>;
    const candidates = [container['content'], container['data'], container['items'], container['results']];
    for (const candidate of candidates) {
      if (Array.isArray(candidate)) {
        return candidate;
      }
    }
    return [];
  }

  private normalizeAppealRecord(raw: unknown): BanAppealRecord | null {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const data = raw as Record<string, unknown>;
    const appealId = this.coerceNumber(data['appealId'] ?? data['appeal_id']);
    const userId = this.coerceNumber(data['userId'] ?? data['user_id']);
    if (appealId == null || userId == null) {
      return null;
    }

    return {
      appealId,
      description: String(data['description'] ?? ''),
      appealStatus: this.coerceString(data['appealStatus'] ?? data['appeal_status']),
      submittedDate: this.coerceString(data['submittedDate'] ?? data['submitted_date']),
      resolvedDate: this.coerceString(data['resolvedDate'] ?? data['resolved_date']),
      reviewedBy: this.coerceString(data['reviewedBy'] ?? data['reviewed_by']),
      viewed: this.coerceBoolean(data['viewed']),
      viewedAt: this.coerceString(data['viewedAt'] ?? data['viewed_at']),
      viewedBy: this.coerceString(data['viewedBy'] ?? data['viewed_by']),
      userId,
    };
  }

  private coerceNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }
    if (typeof value === 'string') {
      const parsed = Number(value.trim());
      return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
  }

  private coerceString(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null;
    }
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  private coerceBoolean(value: unknown): boolean {
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      return value !== 0;
    }
    if (typeof value === 'string') {
      const normalized = value.trim().toLowerCase();
      return normalized === 'true' || normalized === '1' || normalized === 'yes';
    }
    return false;
  }

  private resolveAppealQueueError(err: unknown): string {
    const raw = String(
      (err as { error?: { message?: unknown }; message?: unknown })?.error?.message ??
        (err as { message?: unknown })?.message ??
        'Failed to load appeal queue'
    );
    const normalized = raw.toLowerCase();
    if (normalized.includes('unknown column') || normalized.includes('sqlsyntaxerror')) {
      return 'Ban appeal schema is outdated. Restart the backend so migrations run, then refresh this page.';
    }
    if (normalized.includes('403') || normalized.includes('forbidden') || normalized.includes('access denied')) {
      return 'You need admin access to view the global ban appeal inbox.';
    }
    return raw;
  }

  private applyOverviewPayload(payload: unknown): void {
    const normalized = this.normalizeOverviewPage(payload);
    this.users = normalized.items;
    this.totalUsers = normalized.totalElements;
    this.totalUserPages = Math.max(1, normalized.totalPages);
    this.userPage = Math.min(Math.max(normalized.page + 1, 1), this.totalUserPages);
    this.userPageSize = normalized.size;
    this.totalPendingVerification = normalized.pendingVerificationCount;
    this.totalBannedUsers = normalized.bannedCount;
    this.totalWarnedUsers = normalized.warnedCount;
    this.totalActiveRecently = normalized.activeRecentlyCount;
  }

  private normalizeOverviewPage(payload: unknown): {
    items: AdminUserOverview[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
    pendingVerificationCount: number;
    bannedCount: number;
    warnedCount: number;
    activeRecentlyCount: number;
  } {
    const fallbackSize = this.userPageSize;
    const fallbackPage = Math.max(0, this.userPage - 1);

    if (Array.isArray(payload)) {
      const items = payload as AdminUserOverview[];
      return {
        items,
        totalElements: items.length,
        totalPages: items.length === 0 ? 1 : Math.ceil(items.length / fallbackSize),
        page: fallbackPage,
        size: fallbackSize,
        pendingVerificationCount: items.filter((u) => u.verificationRequired && !u.verificationApproved).length,
        bannedCount: items.filter((u) => u.banned).length,
        warnedCount: items.filter((u) => (u.warningCount ?? 0) > 0).length,
        activeRecentlyCount: items.filter((u) => (u.activityCountLast30Days ?? 0) > 0).length,
      };
    }

    const raw = payload as AdminUserOverviewPageResponse | null | undefined;
    const content = Array.isArray(raw?.content) ? (raw?.content as AdminUserOverview[]) : [];
    const size = Math.max(1, this.coerceNumber(raw?.size) ?? fallbackSize);
    const totalElements = this.coerceNumber(raw?.totalElements) ?? content.length;
    const totalPages = Math.max(
      1,
      this.coerceNumber(raw?.totalPages) ?? (totalElements === 0 ? 1 : Math.ceil(totalElements / size))
    );

    return {
      items: content,
      totalElements,
      totalPages,
      page: this.coerceNumber(raw?.page) ?? fallbackPage,
      size,
      pendingVerificationCount:
        this.coerceNumber(raw?.pendingVerificationCount) ??
        content.filter((u) => u.verificationRequired && !u.verificationApproved).length,
      bannedCount: this.coerceNumber(raw?.bannedCount) ?? content.filter((u) => u.banned).length,
      warnedCount: this.coerceNumber(raw?.warnedCount) ?? content.filter((u) => (u.warningCount ?? 0) > 0).length,
      activeRecentlyCount:
        this.coerceNumber(raw?.activeRecentlyCount) ?? content.filter((u) => (u.activityCountLast30Days ?? 0) > 0).length,
    };
  }
}
