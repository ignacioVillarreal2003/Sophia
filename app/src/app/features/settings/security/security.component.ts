import {ChangeDetectionStrategy, Component, OnInit, computed, inject, signal} from '@angular/core';
import {AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {AppError} from '@core/models/api.model';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {CardComponent} from '@shared/ui/card/card.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {GoogleSigninButtonComponent} from '@features/authentication/google-signin-button.component';
import {firstErrorKey, passwordStrengthValidator} from '@shared/utils/form-errors';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';

function matchPassword(group: AbstractControl) {
  const a = group.get('newPassword')?.value;
  const b = group.get('confirmPassword')?.value;
  return a && b && a !== b ? {mismatch: true} : null;
}

@Component({
  selector: 'app-security',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CardComponent,
    ButtonComponent,
    TextFieldComponent,
    SpinnerComponent,
    PageHeaderComponent,
    GoogleSigninButtonComponent,
    TranslatePipe,
  ],
  templateUrl: './security.component.html',
  styleUrl: './security.component.scss',
})
export class SecurityComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly hasPassword = computed(() => this.auth.user()?.hasPassword ?? true);
  readonly googleLinked = computed(() => this.auth.user()?.googleLinked ?? false);

  readonly loading = signal(false);
  readonly sendingSetPassword = signal(false);
  readonly setPasswordSent = signal(false);

  readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, passwordStrengthValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    {validators: matchPassword},
  );

  readonly changingEmail = signal(false);
  readonly emailForm = this.fb.nonNullable.group({
    newEmail: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly linkingGoogle = signal(false);
  readonly unlinkingGoogle = signal(false);

  readonly deletePassword = new FormControl('', {nonNullable: true});
  readonly deleting = signal(false);

  ngOnInit(): void {
    this.auth.refreshUser().subscribe({
      error: () => {
      }
    });
  }

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    return key ? this.i18n.translate(key) : null;
  }

  confirmError(): string | null {
    const ctrl = this.form.get('confirmPassword');
    if (this.form.errors?.['mismatch'] && (ctrl?.touched || ctrl?.dirty)) {
      return this.i18n.translate('auth.error.mismatch');
    }
    return this.errorOf('confirmPassword');
  }

  emailErrorOf(control: string): string | null {
    const key = firstErrorKey(this.emailForm.get(control));
    return key ? this.i18n.translate(key) : null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    const {currentPassword, newPassword} = this.form.getRawValue();
    this.auth.changePassword({currentPassword, newPassword}).subscribe({
      next: () => {
        this.loading.set(false);
        this.form.reset();
        this.toast.success(this.i18n.translate('settings.security.toast.passwordUpdated'));
      },
      error: (err: AppError) => {
        this.loading.set(false);
        this.toast.error(err.message);
      },
    });
  }

  sendSetPasswordEmail(): void {
    const email = this.user()?.email;
    if (!email) return;
    this.sendingSetPassword.set(true);
    this.auth.forgotPassword({email}).subscribe({
      next: () => {
        this.sendingSetPassword.set(false);
        this.setPasswordSent.set(true);
        this.toast.success(this.i18n.translate('settings.security.toast.setPasswordSent'));
      },
      error: (err: AppError) => {
        this.sendingSetPassword.set(false);
        this.toast.error(err.message);
      },
    });
  }

  submitEmailChange(): void {
    if (this.emailForm.invalid) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.changingEmail.set(true);
    this.auth.changeEmail(this.emailForm.getRawValue()).subscribe({
      next: () => {
        this.changingEmail.set(false);
        this.emailForm.reset();
        this.toast.success(this.i18n.translate('settings.security.toast.emailChangeRequested'));
      },
      error: (err: AppError) => {
        this.changingEmail.set(false);
        this.toast.error(err.message);
      },
    });
  }

  onGoogleLinkCredential(idToken: string): void {
    this.linkingGoogle.set(true);
    this.auth.linkGoogle({idToken}).subscribe({
      next: () => {
        this.linkingGoogle.set(false);
        this.toast.success(this.i18n.translate('settings.security.toast.googleLinked'));
      },
      error: (err: AppError) => {
        this.linkingGoogle.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async unlinkGoogle(): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('settings.security.confirmUnlinkGoogle.title'),
      message: this.i18n.translate('settings.security.confirmUnlinkGoogle.message'),
      confirmLabel: this.i18n.translate('settings.security.confirmUnlinkGoogle.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.unlinkingGoogle.set(true);
    this.auth.unlinkGoogle().subscribe({
      next: () => {
        this.unlinkingGoogle.set(false);
        this.toast.success(this.i18n.translate('settings.security.toast.googleUnlinked'));
      },
      error: (err: AppError) => {
        this.unlinkingGoogle.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async deleteAccount(): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('settings.security.confirmDelete.title'),
      message: this.i18n.translate('settings.security.confirmDelete.message'),
      confirmLabel: this.i18n.translate('settings.security.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;

    this.deleting.set(true);
    const password = this.deletePassword.value;
    this.auth.deleteAccount({password: password || undefined}).subscribe({
      next: () => {
        this.deleting.set(false);
        this.router.navigate(['/authentication/login']);
      },
      error: (err: AppError) => {
        this.deleting.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
