import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {EMAIL_NOT_VERIFIED_MESSAGE} from '@core/models/authentication/authentication.model';
import {AppError} from '@core/models/api.model';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {firstErrorKey} from '@shared/utils/form-errors';
import {deviceLabel} from '@shared/utils/device-label';
import {GoogleSigninButtonComponent} from '../google-signin-button.component';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    CheckboxComponent,
    TextFieldComponent,
    SpinnerComponent,
    TranslatePipe,
    GoogleSigninButtonComponent,
  ],
  templateUrl: './login.component.html',
  styleUrl: '../auth-form.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly googleLoading = signal(false);
  readonly formError = signal<string | null>(null);
  readonly unverifiedEmail = signal<string | null>(null);
  readonly resendingVerification = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    rememberMe: [true],
  });

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    return key ? this.i18n.translate(key) : null;
  }

  submit(): void {
    this.formError.set(null);
    this.unverifiedEmail.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    const {email, password, rememberMe} = this.form.getRawValue();

    this.auth
      .login({email, password, rememberMe, deviceName: deviceLabel(navigator.userAgent, this.i18n.translate)})
      .subscribe({
        next: () => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
          this.router.navigateByUrl(returnUrl);
        },
        error: (err: AppError) => {
          this.loading.set(false);
          if (err.status === 403 && err.message === EMAIL_NOT_VERIFIED_MESSAGE) {
            this.unverifiedEmail.set(email);
            return;
          }
          this.formError.set(
            err.status === 401 || err.status === 400
              ? this.i18n.translate('auth.error.invalid')
              : err.message,
          );
        },
      });
  }

  resendVerification(): void {
    const email = this.unverifiedEmail();
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
    const rememberMe = this.form.getRawValue().rememberMe;

    this.auth
      .loginWithGoogle({idToken, rememberMe, deviceName: deviceLabel(navigator.userAgent, this.i18n.translate)})
      .subscribe({
        next: () => {
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
          this.router.navigateByUrl(returnUrl);
        },
        error: (err: AppError) => {
          this.googleLoading.set(false);
          this.formError.set(err.message);
        },
      });
  }
}
