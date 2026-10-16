import {ChangeDetectionStrategy, Component, inject, input, signal} from '@angular/core';
import {AbstractControl, FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterLink} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {AppError} from '@core/models/api.model';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {firstErrorKey, passwordStrengthValidator} from '@shared/utils/form-errors';

function matchPassword(group: AbstractControl) {
  const pass = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pass && confirm && pass !== confirm ? {mismatch: true} : null;
}

@Component({
  selector: 'app-reset-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, TextFieldComponent, TranslatePipe],
  template: `
    <div class="auth-form">
      <header class="auth-form__head">
        <h1 class="auth-form__title">{{ 'auth.reset.title' | t }}</h1>
      </header>

      @if (formError()) {
        <div class="auth-form__alert" role="alert">{{ formError() }}</div>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="auth-form__field">
          <app-text-field
            formControlName="newPassword"
            type="password"
            icon="lock"
            autocomplete="new-password"
            [label]="'auth.field.newPassword' | t"
            [invalid]="!!errorOf('newPassword')"
            [error]="errorOf('newPassword') ?? ''"
            [hint]="'validation.password' | t"
            required
          />
        </div>
        <div class="auth-form__field">
          <app-text-field
            formControlName="confirmPassword"
            type="password"
            icon="lock"
            autocomplete="new-password"
            [label]="'auth.field.confirmPassword' | t"
            [invalid]="!!confirmError()"
            [error]="confirmError() ?? ''"
            required
          />
        </div>
        <app-button type="submit" variant="primary" size="lg" block [loading]="loading()">
          {{ 'auth.reset.submit' | t }}
        </app-button>
      </form>

      <p class="auth-form__foot">
        <a routerLink="/authentication/login" class="auth-form__link">{{ 'common.back' | t }}</a>
      </p>
    </div>
  `,
  styleUrl: '../../authentication/auth-form.scss',
})
export class ResetPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);

  readonly token = input<string>('');

  readonly loading = signal(false);
  readonly formError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, passwordStrengthValidator]],
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
    if (this.form.invalid || !this.token()) {
      this.form.markAllAsTouched();
      if (!this.token()) this.formError.set(this.i18n.translate('auth.error.invalidExpiredLink'));
      return;
    }
    this.loading.set(true);
    this.auth
      .resetPassword({token: this.token(), newPassword: this.form.getRawValue().newPassword})
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('auth.reset.success'));
          this.router.navigate(['/authentication/login']);
        },
        error: (err: AppError) => {
          this.loading.set(false);
          this.formError.set(err.message);
        },
      });
  }
}
