import {ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal} from '@angular/core';
import {AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterLink} from '@angular/router';
import {Session, User} from '@core/models/authentication';
import {Permission, Role} from '@core/models/admin';
import {AppError} from '@core/models/api.model';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {AVAILABLE_THEMES, themeToApi} from '@core/services/theme.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {SelectFieldComponent, SelectOption} from '@shared/ui/form-field/select-field.component';
import {ChipSelectComponent, ChipOption} from '@shared/ui/chip-select/chip-select.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {IconName} from '@shared/ui/icon/icons';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {LoadMoreButtonComponent} from '@shared/ui/load-more-button/load-more-button.component';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {CollapsibleSectionComponent} from '@shared/ui/collapsible-section/collapsible-section.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {UserNotificationsCardComponent} from '@features/admin/notifications/user-notifications-card.component';
import {firstErrorKey, passwordStrengthValidator} from '@shared/utils/form-errors';
import {deviceLabel} from '@shared/utils/device-label';

const MOBILE_UA_PATTERN = /Mobile|Android|iPhone|iPad/i;

const ERROR_KEYS: Record<string, string> = {
  'validation.required': 'validation.required',
  'validation.email': 'validation.email',
};

function matchPassword(group: AbstractControl) {
  const a = group.get('newPassword')?.value;
  const b = group.get('confirmPassword')?.value;
  return a && b && a !== b ? {mismatch: true} : null;
}

@Component({
  selector: 'app-user-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    CardComponent,
    AvatarComponent,
    BadgeComponent,
    SpinnerComponent,
    TextFieldComponent,
    SelectFieldComponent,
    ChipSelectComponent,
    IconComponent,
    EmptyStateComponent,
    LoadMoreButtonComponent,
    CollapsibleSectionComponent,
    PageHeaderComponent,
    UserNotificationsCardComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './user-detail.component.html',
  styleUrl: './user-detail.component.scss',
})
export class UserDetailComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly id = input.required<string>();

  readonly user = signal<User | null>(null);
  readonly loading = signal(true);
  readonly savingProfile = signal(false);
  readonly savingPreferences = signal(false);
  readonly savingRoles = signal(false);
  readonly savingPermissions = signal(false);
  readonly settingPassword = signal(false);

  readonly sessions = signal<Session[]>([]);
  readonly loadingSessions = signal(false);
  readonly loadingMoreSessions = signal(false);
  readonly sessionsPage = signal(0);
  readonly sessionsLast = signal(true);
  readonly revokingSessionId = signal<string | null>(null);
  readonly revokingAllSessions = signal(false);

  readonly resendingVerification = signal(false);
  readonly markingVerified = signal(false);
  readonly unverifyingEmail = signal(false);
  readonly unlinkingGoogle = signal(false);

  readonly availableRoles = signal<Role[]>([]);
  readonly availablePermissions = signal<Permission[]>([]);
  readonly rolesControl = new FormControl<string[]>([], {nonNullable: true});
  readonly permissionsControl = new FormControl<string[]>([], {nonNullable: true});

  readonly rolesPage = signal(0);
  readonly rolesLast = signal(true);
  readonly loadingMoreRoles = signal(false);
  readonly permissionsPage = signal(0);
  readonly permissionsLast = signal(true);
  readonly loadingMorePermissions = signal(false);

  readonly isSelf = computed(() => this.auth.user()?.id === this.id());
  readonly canUpdate = computed(() => this.auth.permissions().has('USER_UPDATE'));
  readonly canManageRoles = computed(() => this.auth.permissions().has('ROLE_MANAGE'));
  readonly canDelete = computed(
    () => this.auth.permissions().has('USER_DELETE') && !this.isSelf(),
  );
  readonly canResetPassword = computed(
    () => this.auth.permissions().has('USER_PASSWORD_RESET') && !this.isSelf(),
  );
  readonly canViewSessions = computed(
    () =>
      (this.auth.permissions().has('SESSION_READ') || this.auth.permissions().has('SESSION_MANAGE')) &&
      !this.isSelf(),
  );
  readonly canManageSessions = computed(
    () => this.auth.permissions().has('SESSION_MANAGE') && !this.isSelf(),
  );
  readonly canManageVerification = computed(
    () => this.auth.permissions().has('USER_EMAIL_VERIFY') && !this.isSelf(),
  );
  readonly canUnlinkGoogle = computed(
    () => this.auth.permissions().has('USER_GOOGLE_UNLINK') && !this.isSelf(),
  );
  readonly canViewAudit = computed(() => this.auth.permissions().has('AUDIT_READ'));

  readonly sessionsLoaded = signal(false);

  readonly themeOptions = computed<SelectOption[]>(() =>
    AVAILABLE_THEMES.map((t) => ({value: themeToApi(t.id), label: this.i18n.translate(t.labelKey)})),
  );

  readonly themeModeOptions = computed<SelectOption[]>(() => [
    {value: 'LIGHT', label: this.i18n.translate('settings.appearance.mode.light')},
    {value: 'DARK', label: this.i18n.translate('settings.appearance.mode.dark')},
  ]);

  readonly localeOptions = computed<SelectOption[]>(() => {
    const options: SelectOption[] = this.i18n.supported.map((loc) => ({
      value: loc,
      label: loc === 'es' ? 'Español' : loc === 'en' ? 'English' : loc,
    }));
    const current = this.user()?.locale?.split('-')[0];
    if (current && !options.some((o) => o.value === current)) {
      options.push({value: current, label: current});
    }
    return options;
  });

  readonly timezoneOptions = computed<SelectOption[]>(() => {
    const options: SelectOption[] = [{value: 'America/Montevideo', label: 'America/Montevideo'}];
    const current = this.user()?.timezone;
    if (current && !options.some((o) => o.value === current)) {
      options.push({value: current, label: current});
    }
    return options;
  });

  readonly roleChipOptions = computed<ChipOption[]>(() => {
    const options: ChipOption[] = this.availableRoles().map((r) => ({
      value: r.name,
      label: r.name,
      hint: r.description ?? '',
    }));
    const known = new Set(options.map((o) => o.value));
    for (const name of this.user()?.roles ?? []) {
      if (!known.has(name)) {
        options.push({value: name, label: name, hint: this.i18n.translate('admin.users.staleChipHint')});
        known.add(name);
      }
    }
    return options;
  });

  readonly permissionChipOptions = computed<ChipOption[]>(() => {
    const options: ChipOption[] = this.availablePermissions().map((p) => ({
      value: p.name,
      label: p.name,
      hint: p.description ?? '',
    }));
    const known = new Set(options.map((o) => o.value));
    for (const name of this.user()?.directPermissions ?? []) {
      if (!known.has(name)) {
        options.push({value: name, label: name, hint: this.i18n.translate('admin.users.staleChipHint')});
        known.add(name);
      }
    }
    return options;
  });

  readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
  });

  readonly preferencesForm = this.fb.nonNullable.group({
    theme: ['', [Validators.required]],
    themeMode: ['', [Validators.required]],
    locale: ['', [Validators.required]],
    timezone: ['', [Validators.required]],
  });

  readonly passwordForm = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, passwordStrengthValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    {validators: matchPassword},
  );

  ngOnInit(): void {
    this.loadRoles(0);
    this.loadPermissions(0);
    this.load();
  }

  /** Sessions are fetched the first time their section is opened. */
  onSessionsToggled(open: boolean): void {
    if (open && !this.sessionsLoaded()) {
      this.sessionsLoaded.set(true);
      this.loadSessions(0);
    }
  }

  private loadRoles(page: number): void {
    this.api.listRoles({page, size: 20}).subscribe({
      next: (result) => {
        this.availableRoles.update((list) => (page === 0 ? result.content : [...list, ...result.content]));
        this.rolesPage.set(page);
        this.rolesLast.set(result.last);
        this.loadingMoreRoles.set(false);
      },
      error: () => this.loadingMoreRoles.set(false),
    });
  }

  loadMoreRoles(): void {
    this.loadingMoreRoles.set(true);
    this.loadRoles(this.rolesPage() + 1);
  }

  private loadPermissions(page: number): void {
    this.api.listPermissions({page, size: 20}).subscribe({
      next: (result) => {
        this.availablePermissions.update((list) =>
          page === 0 ? result.content : [...list, ...result.content],
        );
        this.permissionsPage.set(page);
        this.permissionsLast.set(result.last);
        this.loadingMorePermissions.set(false);
      },
      error: () => this.loadingMorePermissions.set(false),
    });
  }

  loadMorePermissions(): void {
    this.loadingMorePermissions.set(true);
    this.loadPermissions(this.permissionsPage() + 1);
  }

  private loadSessions(page: number): void {
    if (page === 0) {
      this.loadingSessions.set(true);
    }
    this.api.listUserSessions(this.id(), {page, size: 20}).subscribe({
      next: (result) => {
        this.sessions.update((list) => (page === 0 ? result.content : [...list, ...result.content]));
        this.sessionsPage.set(page);
        this.sessionsLast.set(result.last);
        this.loadingSessions.set(false);
        this.loadingMoreSessions.set(false);
      },
      error: () => {
        this.loadingSessions.set(false);
        this.loadingMoreSessions.set(false);
      },
    });
  }

  loadMoreSessions(): void {
    this.loadingMoreSessions.set(true);
    this.loadSessions(this.sessionsPage() + 1);
  }

  private load(): void {
    this.loading.set(true);
    this.api.getUser(this.id()).subscribe({
      next: (user) => {
        this.applyUser(user);
        this.loading.set(false);
      },
      error: (err: AppError) => {
        this.loading.set(false);
        this.toast.error(err.message);
        this.router.navigate(['/admin/users']);
      },
    });
  }

  private applyUser(user: User): void {
    this.user.set(user);
    this.profileForm.reset({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
    });
    this.preferencesForm.reset({
      theme: user.theme,
      themeMode: user.themeMode,
      locale: user.locale.split('-')[0],
      timezone: user.timezone,
    });
    this.rolesControl.setValue([...user.roles]);
    this.permissionsControl.setValue([...user.directPermissions]);
    this.passwordForm.reset();
  }

  errorOf(form: FormGroup, control: string): string | null {
    const key = firstErrorKey(form.get(control));
    if (!key) return null;
    return this.i18n.translate(ERROR_KEYS[key] ?? 'admin.users.form.error.invalid');
  }

  passwordErrorOf(control: string): string | null {
    const key = firstErrorKey(this.passwordForm.get(control));
    return key ? this.i18n.translate(key) : null;
  }

  confirmPasswordError(): string | null {
    const ctrl = this.passwordForm.get('confirmPassword');
    if (this.passwordForm.errors?.['mismatch'] && (ctrl?.touched || ctrl?.dirty)) {
      return this.i18n.translate('auth.error.mismatch');
    }
    return this.passwordErrorOf('confirmPassword');
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.savingProfile.set(true);
    this.api.patchUser(this.id(), this.profileForm.getRawValue()).subscribe({
      next: (updated) => {
        this.applyUser(updated);
        this.savingProfile.set(false);
        this.toast.success(this.i18n.translate('admin.users.toast.profileUpdated'));
      },
      error: (err: AppError) => {
        this.savingProfile.set(false);
        this.toast.error(err.message);
      },
    });
  }

  savePreferences(): void {
    if (this.preferencesForm.invalid) {
      this.preferencesForm.markAllAsTouched();
      return;
    }
    this.savingPreferences.set(true);
    this.api.patchUser(this.id(), this.preferencesForm.getRawValue()).subscribe({
      next: (updated) => {
        this.applyUser(updated);
        this.savingPreferences.set(false);
        this.toast.success(this.i18n.translate('admin.users.toast.preferencesUpdated'));
      },
      error: (err: AppError) => {
        this.savingPreferences.set(false);
        this.toast.error(err.message);
      },
    });
  }

  saveRoles(): void {
    this.savingRoles.set(true);
    this.api.updateUserRoles(this.id(), {roles: this.rolesControl.value}).subscribe({
      next: (updated) => {
        this.applyUser(updated);
        this.savingRoles.set(false);
        this.toast.success(this.i18n.translate('admin.users.toast.rolesUpdated'));
      },
      error: (err: AppError) => {
        this.savingRoles.set(false);
        this.toast.error(err.message);
      },
    });
  }

  savePermissions(): void {
    this.savingPermissions.set(true);
    this.api
      .updateUserPermissions(this.id(), {permissions: this.permissionsControl.value})
      .subscribe({
        next: (updated) => {
          this.applyUser(updated);
          this.savingPermissions.set(false);
          this.toast.success(this.i18n.translate('admin.users.toast.permissionsUpdated'));
        },
        error: (err: AppError) => {
          this.savingPermissions.set(false);
          this.toast.error(err.message);
        },
      });
  }

  async setPassword(): Promise<void> {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const current = this.user();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmSetPassword.title'),
      message: this.i18n.translate('admin.users.confirmSetPassword.message', {
        name: current.fullName,
      }),
      confirmLabel: this.i18n.translate('admin.users.confirmSetPassword.confirmLabel'),
      danger: true,
    });
    if (!ok) return;

    this.settingPassword.set(true);
    const {newPassword} = this.passwordForm.getRawValue();
    this.api.setUserPassword(this.id(), newPassword).subscribe({
      next: () => {
        this.settingPassword.set(false);
        this.passwordForm.reset();
        this.toast.success(this.i18n.translate('admin.users.toast.passwordSet'));
      },
      error: (err: AppError) => {
        this.settingPassword.set(false);
        this.toast.error(err.message);
      },
    });
  }

  deviceIcon(session: Session): IconName {
    return session.userAgent && MOBILE_UA_PATTERN.test(session.userAgent) ? 'smartphone' : 'monitor';
  }

  deviceLabelOf(session: Session): string {
    return deviceLabel(session.userAgent, this.i18n.translate);
  }

  async revokeSession(session: Session): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmRevokeSession.title'),
      message: this.i18n.translate('admin.users.confirmRevokeSession.message', {
        device: this.deviceLabelOf(session),
      }),
      confirmLabel: this.i18n.translate('admin.users.confirmRevokeSession.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.revokingSessionId.set(session.id);
    this.api.revokeUserSession(this.id(), session.id).subscribe({
      next: () => {
        this.revokingSessionId.set(null);
        this.sessions.update((list) => list.filter((s) => s.id !== session.id));
      },
      error: (err: AppError) => {
        this.revokingSessionId.set(null);
        this.toast.error(err.message);
      },
    });
  }

  async revokeAllSessions(): Promise<void> {
    const current = this.user();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmRevokeAllSessions.title'),
      message: this.i18n.translate('admin.users.confirmRevokeAllSessions.message', {
        name: current.fullName,
      }),
      confirmLabel: this.i18n.translate('admin.users.confirmRevokeAllSessions.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.revokingAllSessions.set(true);
    this.api.revokeAllUserSessions(this.id()).subscribe({
      next: () => {
        this.revokingAllSessions.set(false);
        this.sessions.set([]);
        this.sessionsPage.set(0);
        this.sessionsLast.set(true);
        this.toast.success(this.i18n.translate('admin.users.toast.sessionsRevoked'));
      },
      error: (err: AppError) => {
        this.revokingAllSessions.set(false);
        this.toast.error(err.message);
      },
    });
  }

  resendVerification(): void {
    this.resendingVerification.set(true);
    this.api.resendUserVerification(this.id()).subscribe({
      next: () => {
        this.resendingVerification.set(false);
        this.toast.success(this.i18n.translate('admin.users.toast.verificationResent'));
      },
      error: (err: AppError) => {
        this.resendingVerification.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async markVerified(): Promise<void> {
    const current = this.user();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmMarkVerified.title'),
      message: this.i18n.translate('admin.users.confirmMarkVerified.message', {name: current.fullName}),
      confirmLabel: this.i18n.translate('admin.users.confirmMarkVerified.confirmLabel'),
    });
    if (!ok) return;
    this.markingVerified.set(true);
    this.api.verifyUserEmail(this.id()).subscribe({
      next: () => {
        this.markingVerified.set(false);
        this.user.update((u) => (u ? {...u, emailVerified: true} : u));
        this.toast.success(this.i18n.translate('admin.users.toast.emailVerified'));
      },
      error: (err: AppError) => {
        this.markingVerified.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async unverifyEmail(): Promise<void> {
    const current = this.user();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmUnverifyEmail.title'),
      message: this.i18n.translate('admin.users.confirmUnverifyEmail.message', {name: current.fullName}),
      confirmLabel: this.i18n.translate('admin.users.confirmUnverifyEmail.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.unverifyingEmail.set(true);
    this.api.unverifyUserEmail(this.id()).subscribe({
      next: () => {
        this.unverifyingEmail.set(false);
        this.user.update((u) => (u ? {...u, emailVerified: false} : u));
        this.toast.success(this.i18n.translate('admin.users.toast.emailUnverified'));
      },
      error: (err: AppError) => {
        this.unverifyingEmail.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async unlinkGoogle(): Promise<void> {
    const current = this.user();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmUnlinkGoogle.title'),
      message: this.i18n.translate('admin.users.confirmUnlinkGoogle.message', {name: current.fullName}),
      confirmLabel: this.i18n.translate('admin.users.confirmUnlinkGoogle.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.unlinkingGoogle.set(true);
    this.api.unlinkUserGoogle(this.id()).subscribe({
      next: () => {
        this.unlinkingGoogle.set(false);
        this.user.update((u) => (u ? {...u, googleLinked: false} : u));
        this.toast.success(this.i18n.translate('admin.users.toast.googleUnlinked'));
      },
      error: (err: AppError) => {
        this.unlinkingGoogle.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async toggleStatus(): Promise<void> {
    const current = this.user();
    if (!current) return;
    const nextActive = !current.active;
    const ok = await this.dialog.confirm(
      nextActive
        ? {
          title: this.i18n.translate('admin.users.confirmActivate.title'),
          message: this.i18n.translate('admin.users.confirmActivate.message', {
            name: current.fullName,
          }),
          confirmLabel: this.i18n.translate('admin.users.confirmActivate.confirmLabel'),
        }
        : {
          title: this.i18n.translate('admin.users.confirmDeactivate.title'),
          message: this.i18n.translate('admin.users.confirmDeactivate.message', {
            name: current.fullName,
          }),
          confirmLabel: this.i18n.translate('admin.users.confirmDeactivate.confirmLabel'),
          danger: true,
        },
    );
    if (!ok) return;
    this.api.updateUserStatus(this.id(), {active: nextActive}).subscribe({
      next: (updated) => {
        this.applyUser(updated);
        this.toast.success(
          this.i18n.translate(nextActive ? 'admin.users.toast.activated' : 'admin.users.toast.deactivated'),
        );
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  async deleteUser(): Promise<void> {
    const current = this.user();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.confirmDelete.title'),
      message: this.i18n.translate('admin.users.confirmDelete.message', {name: current.fullName}),
      confirmLabel: this.i18n.translate('admin.users.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.api.deleteUser(this.id()).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('admin.users.toast.deleted'));
        this.router.navigate(['/admin/users']);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }
}
