import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { firstErrorKey } from '@shared/utils/form-errors';

@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, TextFieldComponent, TranslatePipe],
  template: `
    <div class="auth-form">
      <header class="auth-form__head">
        <h1 class="auth-form__title">{{ 'auth.forgot.title' | t }}</h1>
      </header>

      @if (sent()) {
        <div class="auth-form__success">{{ 'auth.forgot.sent' | t }}</div>
        <app-button variant="secondary" size="lg" block routerLink="/authentication/login">
          {{ 'common.back' | t }}
        </app-button>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="auth-form__field">
            <app-text-field
              formControlName="email"
              type="email"
              icon="user"
              autocomplete="email"
              [label]="'auth.field.email' | t"
              [placeholder]="'auth.field.emailPlaceholder' | t"
              [invalid]="!!errorOf('email')"
              [error]="errorOf('email') ?? ''"
              required
            />
          </div>
          <app-button type="submit" variant="primary" size="lg" block [loading]="loading()">
            {{ 'auth.forgot.submit' | t }}
          </app-button>
        </form>

        <p class="auth-form__foot">
          <a routerLink="/authentication/login" class="auth-form__link">{{ 'common.back' | t }}</a>
        </p>
      }
    </div>
  `,
  styleUrl: '../auth-form.scss',
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);

  readonly loading = signal(false);
  readonly sent = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    return key ? this.i18n.translate(key) : null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.auth.forgotPassword(this.form.getRawValue()).subscribe({
      next: () => this.sent.set(true),
      error: () => this.sent.set(true),
    });
  }
}
