import {ChangeDetectionStrategy, Component, computed, effect, inject} from '@angular/core';
import {FormControl, ReactiveFormsModule} from '@angular/forms';
import {
  AVAILABLE_THEMES,
  ThemeMode,
  ThemeService,
  VisualTheme,
  modeToApi,
  themeToApi,
} from '@core/services/theme.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {ToastService} from '@core/services/toast.service';
import {CardComponent} from '@shared/ui/card/card.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SelectFieldComponent, SelectOption} from '@shared/ui/form-field/select-field.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';

@Component({
  selector: 'app-appearance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CardComponent,
    IconComponent,
    SelectFieldComponent,
    PageHeaderComponent,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'settings.appearance.title' | t"/>

    <app-card class="ap__card">
      <h3 class="ap__label">{{ 'nav.theme' | t }}</h3>
      <div class="ap__options">
        @for (opt of themes; track opt.id) {
          <button
            class="ap__option"
            [class.ap__option--active]="theme.theme() === opt.id"
            (click)="selectTheme(opt.id)"
          >
            <span class="ap__swatch" [attr.data-palette]="opt.id"></span>
            <span>{{ opt.labelKey | t }}</span>
          </button>
        }
      </div>
    </app-card>

    <app-card class="ap__card">
      <h3 class="ap__label">{{ 'settings.appearance.mode.title' | t }}</h3>
      <div class="ap__options">
        <button
          class="ap__option"
          [class.ap__option--active]="theme.mode() === 'light'"
          (click)="selectMode('light')"
        >
          <app-icon name="sun" [size]="22"/>
          <span>{{ 'settings.appearance.mode.light' | t }}</span>
        </button>
        <button
          class="ap__option"
          [class.ap__option--active]="theme.mode() === 'dark'"
          (click)="selectMode('dark')"
        >
          <app-icon name="moon" [size]="22"/>
          <span>{{ 'settings.appearance.mode.dark' | t }}</span>
        </button>
      </div>
    </app-card>

    <app-card class="ap__card">
      <h3 class="ap__label">{{ 'nav.language' | t }}</h3>
      <div class="ap__options">
        @for (loc of i18n.supported; track loc) {
          <button
            class="ap__option"
            [class.ap__option--active]="i18n.locale() === loc"
            (click)="selectLocale(loc)"
          >
            <app-icon name="globe" [size]="22"/>
            <span>{{ loc === 'es' ? 'Español' : 'English' }}</span>
          </button>
        }
      </div>
    </app-card>

    <app-card class="ap__card">
      <h3 class="ap__label">{{ 'nav.timezone' | t }}</h3>
      <app-select-field [formControl]="timezoneControl" [options]="timezoneOptions()"/>
    </app-card>
  `,
  styleUrl: './appearance.component.scss',
})
export class AppearanceComponent {
  readonly theme = inject(ThemeService);
  readonly i18n = inject(TranslationService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);

  readonly themes = AVAILABLE_THEMES;

  readonly timezoneOptions = computed<SelectOption[]>(() => {
    const options: SelectOption[] = [{value: 'America/Montevideo', label: 'America/Montevideo'}];
    const current = this.auth.user()?.timezone;
    if (current && !options.some((o) => o.value === current)) {
      options.push({value: current, label: current});
    }
    return options;
  });

  readonly timezoneControl = new FormControl<string>('', {nonNullable: true});

  constructor() {
    effect(() => {
      const tz = this.auth.user()?.timezone;
      if (tz && tz !== this.timezoneControl.value) {
        this.timezoneControl.setValue(tz, {emitEvent: false});
      }
    });

    this.timezoneControl.valueChanges.subscribe((timezone) => {
      this.auth.updatePreferences({timezone}).subscribe({
        error: () => this.toast.error(this.i18n.translate('settings.appearance.error.timezone')),
      });
    });
  }

  selectTheme(id: VisualTheme): void {
    this.theme.set(id);
    this.auth.updatePreferences({theme: themeToApi(id)}).subscribe({
      error: () => this.toast.error(this.i18n.translate('settings.appearance.error.theme')),
    });
  }

  selectMode(mode: ThemeMode): void {
    this.theme.setMode(mode);
    this.auth.updatePreferences({themeMode: modeToApi(mode)}).subscribe({
      error: () => this.toast.error(this.i18n.translate('settings.appearance.error.theme')),
    });
  }

  selectLocale(loc: string): void {
    this.i18n.setLocale(loc);
    this.auth.updatePreferences({locale: loc}).subscribe({
      error: () => this.toast.error(this.i18n.translate('settings.appearance.error.locale')),
    });
  }
}
