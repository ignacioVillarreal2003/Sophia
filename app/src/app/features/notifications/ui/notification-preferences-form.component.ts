import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  NOTIFICATION_CHANNELS,
  NotificationChannel,
  NotificationPreferences,
  NotificationPreferencesUpdate,
} from '@core/models/notifications';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CheckboxComponent } from '@shared/ui/checkbox/checkbox.component';
import { IconComponent } from '@shared/ui/icon/icon.component';

/** One row of the preference matrix. Mandatory categories are shown but locked. */
export interface PreferenceCategoryRow {
  code: string;
  label: string;
  mandatory: boolean;
}

/**
 * The channel/category preference matrix, shared by the user's own settings
 * page and the administration view that edits another user's preferences.
 */
@Component({
  selector: 'app-notification-preferences-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, CheckboxComponent, IconComponent, TranslatePipe],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()">
      <div formGroupName="channels">
        @for (channel of channels; track channel) {
          <div class="npf__row">
            <div class="npf__row-text">
              <span class="npf__label">{{ ('settings.notifications.channel.' + channel) | t }}</span>
              <span class="npf__hint">{{ hintFor(channel) | t }}</span>
            </div>
            <app-checkbox [formControlName]="channel"/>
          </div>
        }
      </div>

      <div class="npf__section-title">{{ 'settings.notifications.categoriesTitle' | t }}</div>

      <div class="npf__category-header" [style.grid-template-columns]="gridColumns">
        <span></span>
        @for (channel of channels; track channel) {
          <span class="npf__category-col">{{ ('settings.notifications.channel.' + channel) | t }}</span>
        }
      </div>

      @for (category of mandatoryCategories(); track category.code) {
        <div class="npf__category-row npf__category-row--locked" [style.grid-template-columns]="gridColumns">
          <span class="npf__label">{{ category.label }}</span>
          @for (channel of channels; track channel) {
            <span class="npf__category-col"><app-icon name="check" [size]="16"/></span>
          }
        </div>
      }

      <div formGroupName="categories">
        @for (category of editableCategories(); track category.code) {
          <div class="npf__category-row" [formGroupName]="category.code" [style.grid-template-columns]="gridColumns">
            <span class="npf__label">{{ category.label }}</span>
            @for (channel of channels; track channel) {
              <span class="npf__category-col"><app-checkbox [formControlName]="channel"/></span>
            }
          </div>
        }
      </div>

      <div class="npf__actions">
        <app-button variant="primary" type="submit" [loading]="saving()">
          {{ 'common.saveChanges' | t }}
        </app-button>
      </div>
    </form>
  `,
  styles: [
    `
      .npf__row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-md);
        padding-block: var(--space-sm);

        &:not(:last-of-type) {
          border-bottom: 1px solid var(--divider);
        }
      }

      .npf__row-text {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }

      .npf__label {
        font-size: var(--type-body-strong-size);
        font-weight: 600;
        color: var(--text);
      }

      .npf__hint {
        font-size: var(--type-caption-size);
        color: var(--text-muted);
      }

      .npf__section-title {
        margin-top: var(--space-lg);
        padding-top: var(--space-md);
        border-top: 1px solid var(--divider);
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text-muted);
        text-transform: uppercase;
        letter-spacing: 0.02em;
      }

      .npf__category-header,
      .npf__category-row {
        display: grid;
        align-items: center;
        gap: var(--space-md);
      }

      .npf__category-header {
        padding-top: var(--space-sm);
        font-size: var(--type-fine-size);
        color: var(--text-subtle);
      }

      .npf__category-row {
        padding-block: var(--space-sm);
        border-bottom: 1px solid var(--divider);

        &:last-child {
          border-bottom: none;
        }
      }

      .npf__category-row--locked {
        color: var(--text-subtle);
      }

      .npf__category-col {
        display: flex;
        justify-content: center;
      }

      .npf__actions {
        display: flex;
        justify-content: flex-end;
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class NotificationPreferencesFormComponent {
  private readonly fb = inject(FormBuilder);

  private static readonly CHANNEL_HINTS: Record<NotificationChannel, string> = {
    EMAIL: 'settings.notifications.emailHint',
    IN_APP: 'settings.notifications.inAppHint',
  };

  readonly categories = input.required<PreferenceCategoryRow[]>();
  readonly preferences = input<NotificationPreferences | null>(null);
  readonly saving = input(false);

  readonly saved = output<NotificationPreferencesUpdate>();

  readonly channels = NOTIFICATION_CHANNELS;
  readonly gridColumns = `1fr repeat(${this.channels.length}, 96px)`;

  readonly form = this.fb.nonNullable.group({
    channels: this.fb.nonNullable.group(
      Object.fromEntries(this.channels.map((channel) => [channel, true])),
    ),
    categories: this.fb.nonNullable.group({}),
  });

  constructor() {
    // The catalogue and the stored values arrive asynchronously and
    // independently; rebuild the matrix whenever either of them changes.
    effect(() => {
      const categories = this.editableCategories();
      const prefs = this.preferences();
      const group = this.fb.nonNullable.group(
        Object.fromEntries(
          categories.map((category) => [
            category.code,
            this.fb.nonNullable.group(
              Object.fromEntries(
                this.channels.map((channel) => [
                  channel,
                  prefs?.categories?.[category.code]?.[channel] ?? true,
                ]),
              ),
            ),
          ]),
        ),
      );
      this.form.setControl('categories', group, { emitEvent: false });
      this.form.controls.channels.patchValue(
        Object.fromEntries(this.channels.map((channel) => [channel, prefs?.channels?.[channel] ?? true])),
        { emitEvent: false },
      );
    });
  }

  mandatoryCategories(): PreferenceCategoryRow[] {
    return this.categories().filter((category) => category.mandatory);
  }

  editableCategories(): PreferenceCategoryRow[] {
    return this.categories().filter((category) => !category.mandatory);
  }

  hintFor(channel: NotificationChannel): string {
    return NotificationPreferencesFormComponent.CHANNEL_HINTS[channel] ?? '';
  }

  submit(): void {
    const value = this.form.getRawValue();
    this.saved.emit({
      channels: value.channels as NotificationPreferencesUpdate['channels'],
      categories: value.categories as NotificationPreferencesUpdate['categories'],
    });
  }
}
