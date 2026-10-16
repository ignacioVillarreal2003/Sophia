import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {
  CONFIGURABLE_NOTIFICATION_CATEGORIES,
  NotificationPreferences,
  NotificationPreferencesUpdate,
} from '@core/models/notifications';
import {NotificationService} from '@features/notifications/data/notification.service';
import {
  NotificationPreferencesFormComponent,
  PreferenceCategoryRow,
} from '@features/notifications/ui/notification-preferences-form.component';
import {AppError} from '@core/models/api.model';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {CardComponent} from '@shared/ui/card/card.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';

@Component({
  selector: 'app-notification-preferences',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
    PageHeaderComponent,
    SpinnerComponent,
    NotificationPreferencesFormComponent,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'settings.notifications.title' | t"/>

    @if (loading()) {
      <div class="np__loading"><app-spinner/></div>
    } @else {
      <app-card>
        <app-notification-preferences-form
          [categories]="categories()"
          [preferences]="preferences()"
          [saving]="saving()"
          (saved)="save($event)"
        />
      </app-card>
    }
  `,
  styles: [
    `
      .np__loading {
        display: grid;
        place-items: center;
        padding: var(--space-section);
      }
    `,
  ],
})
export class NotificationPreferencesComponent {
  private readonly notifications = inject(NotificationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly preferences = signal<NotificationPreferences | null>(null);

  /**
   * Reading the catalogue needs an administration permission, so the user's
   * own page lists the categories the platform seeds.
   */
  readonly categories = computed<PreferenceCategoryRow[]>(() => [
    {
      code: 'AUTHENTICATION_SECURITY',
      label: this.i18n.translate('settings.notifications.categories.AUTHENTICATION_SECURITY'),
      mandatory: true,
    },
    ...CONFIGURABLE_NOTIFICATION_CATEGORIES.map((code) => ({
      code,
      label: this.i18n.translate(`settings.notifications.categories.${code}`),
      mandatory: false,
    })),
  ]);

  constructor() {
    this.notifications.preferences().subscribe({
      next: (prefs) => {
        this.preferences.set(prefs);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  save(patch: NotificationPreferencesUpdate): void {
    this.saving.set(true);
    this.notifications.updatePreferences(patch).subscribe({
      next: (prefs) => {
        this.preferences.set(prefs);
        this.saving.set(false);
        this.toast.success(this.i18n.translate('settings.notifications.toast.updated'));
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
