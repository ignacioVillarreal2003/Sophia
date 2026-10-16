import {ChangeDetectionStrategy, Component, OnInit, inject, input, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {IconComponent} from '@shared/ui/icon/icon.component';

@Component({
  selector: 'app-verify-email',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ButtonComponent, SpinnerComponent, IconComponent, TranslatePipe],
  template: `
    <div class="auth-form auth-form__status">
      @switch (state()) {
        @case ('loading') {
          <app-spinner [size]="32"/>
          <p class="auth-form__subtitle auth-form__status-action">{{ 'common.loading' | t }}</p>
        }
        @case ('ok') {
          <div class="verify__icon verify__icon--ok">
            <app-icon name="check-circle" [size]="30"/>
          </div>
          <h1 class="auth-form__title">{{ 'auth.verify.success.title' | t }}</h1>
          <p class="auth-form__subtitle">{{ 'auth.verify.success.subtitle' | t }}</p>
          <app-button class="auth-form__status-action" variant="secondary" size="lg" block
                      routerLink="/authentication/login">
            {{ 'auth.login.submit' | t }}
          </app-button>
        }
        @case ('error') {
          <div class="verify__icon verify__icon--err">
            <app-icon name="alert-circle" [size]="30"/>
          </div>
          <h1 class="auth-form__title">{{ 'auth.error.invalidLink' | t }}</h1>
          <p class="auth-form__subtitle">{{ 'auth.verify.error.subtitle' | t }}</p>
          <app-button class="auth-form__status-action" variant="secondary" size="lg" block
                      routerLink="/authentication/login">
            {{ 'common.back' | t }}
          </app-button>
        }
      }
    </div>
  `,
  styleUrl: '../../authentication/auth-form.scss',
})
export class VerifyEmailComponent implements OnInit {
  private readonly auth = inject(AuthenticationService);

  readonly token = input<string>('');
  readonly state = signal<'loading' | 'ok' | 'error'>('loading');

  ngOnInit(): void {
    if (!this.token()) {
      this.state.set('error');
      return;
    }
    this.auth.verifyEmail(this.token()).subscribe({
      next: () => this.state.set('ok'),
      error: () => this.state.set('error'),
    });
  }
}
