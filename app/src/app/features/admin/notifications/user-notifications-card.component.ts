import {ChangeDetectionStrategy, Component, computed, inject, input, signal} from '@angular/core';
import {
  AppNotification,
  NotificationCategoryInfo,
  NotificationPreferences,
  NotificationPreferencesUpdate,
  NotificationSeverity,
} from '@core/models/notifications';
import {AppError} from '@core/models/api.model';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {
  NotificationPreferencesFormComponent,
  PreferenceCategoryRow,
} from '@features/notifications/ui/notification-preferences-form.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CollapsibleSectionComponent} from '@shared/ui/collapsible-section/collapsible-section.component';
import {BadgeComponent, BadgeTone} from '@shared/ui/badge/badge.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {NotificationAdminService} from './data/notification-admin.service';

const INBOX_PAGE_SIZE = 5;

const SEVERITY_TONES: Record<NotificationSeverity, BadgeTone> = {
  INFO: 'neutral',
  SUCCESS: 'success',
  WARNING: 'warning',
  ERROR: 'danger',
};

/**
 * Notification management for one user, shown inside the admin user detail:
 * their delivery preferences (editable on their behalf) and a read-only view
 * of what they have actually received.
 */
@Component({
  selector: 'app-user-notifications-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ButtonComponent,
    CollapsibleSectionComponent,
    BadgeComponent,
    SpinnerComponent,
    NotificationPreferencesFormComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  template: `
    @if (canReadPreferences()) {
      <app-collapsible-section
        icon="bell"
        [title]="'admin.users.notifications.title' | t"
        [hint]="'admin.users.notifications.hint' | t"
        (openChange)="onPreferencesToggled($event)"
      >
        @if (loadingPreferences()) {
          <div class="unc__loading"><app-spinner/></div>
        } @else if (categories().length === 0) {
          <p class="unc__hint">{{ 'admin.users.notifications.noCatalogue' | t }}</p>
        } @else if (canUpdatePreferences()) {
          <app-notification-preferences-form
            [categories]="categories()"
            [preferences]="preferences()"
            [saving]="saving()"
            (saved)="savePreferences($event)"
          />
        } @else {
          <ul class="unc__readonly">
            @for (row of readonlySummary(); track row.label) {
              <li class="unc__readonly-row">
                <span>{{ row.label }}</span>
                <span class="unc__readonly-value">{{ row.value }}</span>
              </li>
            }
          </ul>
        }
      </app-collapsible-section>
    }

    @if (canReadInbox()) {
      <app-collapsible-section
        icon="inbox"
        [title]="'admin.users.notifications.inbox' | t"
        [hint]="'admin.users.notifications.inboxHint' | t"
        (openChange)="onInboxToggled($event)"
      >
        <div class="unc__inbox-head">
          <app-button variant="ghost" size="sm" (click)="toggleUnreadOnly()">
            {{ (unreadOnly() ? 'admin.users.notifications.showAll' : 'admin.users.notifications.showUnread') | t }}
          </app-button>
        </div>

        @if (loadingInbox()) {
          <div class="unc__loading"><app-spinner/></div>
        } @else if (notifications().length === 0) {
          <p class="unc__empty">{{ 'admin.users.notifications.inboxEmpty' | t }}</p>
        } @else {
          <ul class="unc__list">
            @for (n of notifications(); track n.id) {
              <li class="unc__row" [class.unc__row--unread]="!n.read">
                <div class="unc__row-main">
                  <span class="unc__row-title">{{ n.title }}</span>
                  @if (n.body) { <span class="unc__row-body">{{ n.body }}</span> }
                  <span class="unc__row-meta">{{ n.category }} · {{ n.createdAt | relativeTime }}</span>
                </div>
                <app-badge [tone]="toneFor(n.severity)">{{ n.severity }}</app-badge>
                @if (!n.read) {
                  <app-badge tone="accent">{{ 'admin.users.notifications.unread' | t }}</app-badge>
                }
              </li>
            }
          </ul>

          @if (!inboxLast()) {
            <div class="unc__more">
              <app-button variant="ghost" size="sm" [loading]="loadingMore()" (click)="loadMore()">
                {{ 'common.loadMore' | t }}
              </app-button>
            </div>
          }
        }
      </app-collapsible-section>
    }
  `,
  styles: [
    `
      /* The sections belong to the page's own stack, not to a wrapper. */
      :host {
        display: contents;
      }

      .unc__hint {
        font-size: var(--type-fine-size);
        color: var(--text-muted);
        line-height: 1.5;
      }

      .unc__loading {
        display: grid;
        place-items: center;
        padding: var(--space-lg);
      }

      .unc__inbox-head {
        display: flex;
        justify-content: flex-end;
      }

      .unc__list,
      .unc__readonly {
        list-style: none;
        margin: 0;
        padding: 0;
      }

      .unc__row,
      .unc__readonly-row {
        display: flex;
        align-items: center;
        gap: var(--space-sm);
        padding-block: var(--space-sm);

        &:not(:last-child) {
          border-bottom: 1px solid var(--divider);
        }
      }

      .unc__readonly-value {
        margin-left: auto;
        color: var(--text-muted);
        font-size: var(--type-caption-size);
      }

      .unc__row-main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 1px;
      }

      .unc__row-title {
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }

      .unc__row-body,
      .unc__row-meta {
        font-size: var(--type-fine-size);
        color: var(--text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .unc__row-meta {
        color: var(--text-subtle);
      }

      .unc__empty {
        font-size: var(--type-caption-size);
        color: var(--text-muted);
      }

      .unc__more {
        display: flex;
        justify-content: center;
        margin-top: var(--space-sm);
      }
    `,
  ],
})
export class UserNotificationsCardComponent {
  private readonly service = inject(NotificationAdminService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly userId = input.required<string>();

  readonly categories = signal<PreferenceCategoryRow[]>([]);
  readonly preferences = signal<NotificationPreferences | null>(null);
  readonly loadingPreferences = signal(true);
  readonly saving = signal(false);

  readonly preferencesLoaded = signal(false);
  readonly inboxLoaded = signal(false);

  readonly notifications = signal<AppNotification[]>([]);
  readonly loadingInbox = signal(true);
  readonly loadingMore = signal(false);
  readonly inboxPage = signal(0);
  readonly inboxLast = signal(true);
  readonly unreadOnly = signal(false);

  readonly canReadPreferences = computed(() =>
    this.auth.permissions().has('NOTIFICATION_PREFERENCE_READ_UNRESTRICTED'),
  );
  readonly canUpdatePreferences = computed(() =>
    this.auth.permissions().has('NOTIFICATION_PREFERENCE_UPDATE_UNRESTRICTED'),
  );
  readonly canReadInbox = computed(() =>
    this.auth.permissions().has('NOTIFICATION_READ_UNRESTRICTED'),
  );

  /**
   * Both halves load on first open rather than with the page: reading another
   * user's inbox writes an audit entry, so it should only happen when an
   * administrator actually looks.
   */
  onPreferencesToggled(open: boolean): void {
    if (open && !this.preferencesLoaded()) {
      this.preferencesLoaded.set(true);
      this.loadPreferences(this.userId());
    }
  }

  onInboxToggled(open: boolean): void {
    if (open && !this.inboxLoaded()) {
      this.inboxLoaded.set(true);
      this.inboxPage.set(0);
      this.loadInbox(this.userId(), false);
    }
  }

  toneFor(severity: NotificationSeverity): BadgeTone {
    return SEVERITY_TONES[severity] ?? 'neutral';
  }

  /** Flat summary used when the caller may read but not change the preferences. */
  readonlySummary(): {label: string; value: string}[] {
    const prefs = this.preferences();
    if (!prefs) {
      return [];
    }
    const on = this.i18n.translate('admin.users.notifications.on');
    const off = this.i18n.translate('admin.users.notifications.off');
    return this.categories().map((category) => {
      const overrides = prefs.categories?.[category.code] ?? {};
      const value = category.mandatory
        ? this.i18n.translate('admin.notifications.categories.mandatoryBadge')
        : (['EMAIL', 'IN_APP'] as const)
            .map((channel) => `${channel}: ${overrides[channel] ?? true ? on : off}`)
            .join(' · ');
      return {label: category.label, value};
    });
  }

  toggleUnreadOnly(): void {
    this.unreadOnly.update((value) => !value);
    this.inboxPage.set(0);
    this.loadInbox(this.userId(), false);
  }

  loadMore(): void {
    this.inboxPage.update((page) => page + 1);
    this.loadInbox(this.userId(), true);
  }

  savePreferences(patch: NotificationPreferencesUpdate): void {
    this.saving.set(true);
    this.service.updateUserPreferences(this.userId(), patch).subscribe({
      next: (prefs) => {
        this.preferences.set(prefs);
        this.saving.set(false);
        this.toast.success(this.i18n.translate('admin.users.notifications.toast.updated'));
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  private loadPreferences(userId: string): void {
    this.loadingPreferences.set(true);
    this.service.userPreferences(userId).subscribe({
      next: (prefs) => {
        this.preferences.set(prefs);
        this.loadCatalogue();
      },
      error: (err: AppError) => {
        this.loadingPreferences.set(false);
        this.toast.error(err.message);
      },
    });
  }

  /**
   * The catalogue drives the matrix, so a category added after this build
   * still shows up. Without the permission to read it, fall back to the codes
   * the user already has stored preferences for.
   */
  private loadCatalogue(): void {
    if (!this.auth.permissions().has('NOTIFICATION_CATEGORY_READ')) {
      this.categories.set(
        Object.keys(this.preferences()?.categories ?? {}).map((code) => ({
          code,
          label: this.categoryLabel(code),
          mandatory: false,
        })),
      );
      this.loadingPreferences.set(false);
      return;
    }
    this.service.categories().subscribe({
      next: (result) => {
        this.categories.set(
          result.content.map((category: NotificationCategoryInfo) => ({
            code: category.code,
            label: category.name || this.categoryLabel(category.code),
            mandatory: category.mandatory,
          })),
        );
        this.loadingPreferences.set(false);
      },
      error: () => this.loadingPreferences.set(false),
    });
  }

  private categoryLabel(code: string): string {
    const label = this.i18n.translate(`settings.notifications.categories.${code}`);
    return label.startsWith('settings.notifications.categories.') ? code : label;
  }

  private loadInbox(userId: string, append: boolean): void {
    if (append) {
      this.loadingMore.set(true);
    } else {
      this.loadingInbox.set(true);
    }
    this.service
      .userNotifications(userId, this.inboxPage(), INBOX_PAGE_SIZE, {unreadOnly: this.unreadOnly()})
      .subscribe({
        next: (result) => {
          this.notifications.update((current) =>
            append ? [...current, ...result.content] : result.content,
          );
          this.inboxLast.set(result.last);
          this.loadingInbox.set(false);
          this.loadingMore.set(false);
        },
        error: (err: AppError) => {
          this.loadingInbox.set(false);
          this.loadingMore.set(false);
          this.toast.error(err.message);
        },
      });
  }
}
