import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {AbstractControl, FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterLink} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {EMAIL_NOT_VERIFIED_MESSAGE} from '@core/models/authentication/authentication.model';
import {AppError} from '@core/models/api.model';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {firstErrorKey, passwordStrengthValidator} from '@shared/utils/form-errors';
import {deviceLabel} from '@shared/utils/device-label';
import {GoogleSigninButtonComponent} from '../google-signin-button.component';

function matchPassword(group: AbstractControl) {
  const pass = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pass && confirm && pass !== confirm ? {mismatch: true} : null;
}

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    TextFieldComponent,
    SpinnerComponent,
    IconComponent,
    TranslatePipe,
    GoogleSigninButtonComponent,
  ],
  templateUrl: './register.component.html',
  styleUrl: '../auth-form.scss',
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly googleLoading = signal(false);
  readonly formError = signal<string | null>(null);
  readonly registeredEmail = signal<string | null>(null);
  readonly resendingVerification = signal(false);

  readonly form = this.fb.nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, passwordStrengthValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    {validators: matchPassword},
  );

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

  submit(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    const {email, password} = this.form.getRawValue();

    // Name is optional at registration; the user completes it later in profile.
    this.auth.register({email, password}).subscribe({
      next: () => {
        // Auto-login only succeeds when email verification isn't required;
        // otherwise show the "check your email" screen instead of bouncing
        // the user to /login with no explanation.
        this.auth.login({email, password}).subscribe({
          next: () => this.router.navigateByUrl('/'),
          error: (err: AppError) => {
            this.loading.set(false);
            if (err.status === 403 && err.message === EMAIL_NOT_VERIFIED_MESSAGE) {
              this.registeredEmail.set(email);
            } else {
              this.toast.error(err.message);
              this.router.navigate(['/authentication/login']);
            }
          },
        });
      },
      error: (err: AppError) => {
        this.loading.set(false);
        this.formError.set(err.fieldErrors?.['email'] ?? err.message);
      },
    });
  }

  resendVerification(): void {
    const email = this.registeredEmail();
    if (!email) return;
    this.resendingVerification.set(true);
    this.auth.resendVerification({email}).subscribe({
      next: () => {
        this.resendingVerification.set(false);
        this.toast.success(this.i18n.translate('auth.verify.resent'));
      },
      error: (err: AppError) => {
        this.resendingVerification.set(false);
        this.toast.error(err.message);
      },
    });
  }

  onGoogleCredential(idToken: string): void {
    this.formError.set(null);
    this.googleLoading.set(true);

    this.auth.loginWithGoogle({idToken, deviceName: deviceLabel(navigator.userAgent, this.i18n.translate)}).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err: AppError) => {
        this.googleLoading.set(false);
        this.formError.set(err.message);
      },
    });
  }
}
