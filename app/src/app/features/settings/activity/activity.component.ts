import {ChangeDetectionStrategy, Component, OnInit, inject, signal} from '@angular/core';
import {AuditLogEntry} from '@core/models/authentication';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {CardComponent} from '@shared/ui/card/card.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {IconName} from '@shared/ui/icon/icons';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';

const ACTION_ICONS: Record<string, IconName> = {
  LOGIN: 'arrow-right',
  LOGIN_GOOGLE: 'arrow-right',
  LOGIN_FAILED: 'x',
  ACCOUNT_LOCKED: 'lock',
  LOGOUT: 'logout',
  LOGOUT_ALL: 'logout',
  EMAIL_VERIFIED: 'check',
  PASSWORD_RESET: 'lock',
  PASSWORD_CHANGED: 'lock',
  EMAIL_CHANGE_REQUESTED: 'edit',
  EMAIL_CHANGED: 'edit',
  ACCOUNT_DELETED: 'trash',
  GOOGLE_LINKED: 'link',
  GOOGLE_UNLINKED: 'link',
  USER_UPDATED: 'user',
  USER_PHOTO_UPDATED: 'image',
  SESSION_REVOKED: 'monitor',
  SESSIONS_REVOKED_ALL: 'monitor',
};

@Component({
  selector: 'app-activity',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
    IconComponent,
    BadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PaginationComponent,
    PageHeaderComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './activity.component.html',
  styleUrl: './activity.component.scss',
})
export class ActivityComponent implements OnInit {
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);

  readonly entries = signal<AuditLogEntry[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  ngOnInit(): void {
    this.load();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  labelOf(entry: AuditLogEntry): string {
    const key = `activity.action.${entry.action}`;
    const translated = this.i18n.translate(key);
    if (translated !== key) {
      return translated;
    }
    const lower = entry.action.replace(/_/g, ' ').toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
  }

  iconOf(entry: AuditLogEntry): IconName {
    return ACTION_ICONS[entry.action] ?? 'clock';
  }

  private load(): void {
    this.loading.set(true);
    this.auth.myActivity({page: this.page(), size: 20, sort: 'createdAt,desc'}).subscribe({
      next: (result) => {
        this.entries.set(result.content);
        this.totalPages.set(result.totalPages);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
