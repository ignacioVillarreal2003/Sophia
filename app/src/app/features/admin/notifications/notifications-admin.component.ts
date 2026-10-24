import {ChangeDetectionStrategy, Component, OnInit, computed, inject, signal} from '@angular/core';
import {JsonPipe} from '@angular/common';
import {FormControl, ReactiveFormsModule} from '@angular/forms';
import {firstValueFrom} from 'rxjs';
import {
  DeadLetterReplayResult,
  NotificationAuditEntry,
  NotificationCategoryInfo,
} from '@core/models/notifications';
import {AppError} from '@core/models/api.model';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {SelectFieldComponent, SelectOption} from '@shared/ui/form-field/select-field.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {NotificationAdminService} from './data/notification-admin.service';
import { bindUrlState } from '@shared/utils/url-state';
import {
  CategoryDialogComponent,
  CategoryDialogData,
  CategoryDialogResult,
} from './category-dialog.component';

const AUDIT_PAGE_SIZE = 15;
const DEAD_LETTER_BATCH = 100;

/** Dead letter topics this service owns; both are replayable from here. */
const DEAD_LETTER_TOPICS = [
  'sophia.notifications.commands.dlt',
  'sophia.authentication.user-events.dlt',
];

@Component({
  selector: 'app-notifications-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    JsonPipe,
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    BadgeComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    SelectFieldComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './notifications-admin.component.html',
  styleUrl: './notifications-admin.component.scss',
})
export class NotificationsAdminComponent implements OnInit {
  private readonly service = inject(NotificationAdminService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly i18n = inject(TranslationService);

  readonly categories = signal<NotificationCategoryInfo[]>([]);
  readonly loadingCategories = signal(true);
  readonly busyCode = signal<string | null>(null);

  readonly auditEntries = signal<NotificationAuditEntry[]>([]);
  readonly loadingAudit = signal(true);
  readonly auditPage = signal(0);

  private readonly urlState = bindUrlState([{ signal: this.auditPage, param: 'page', page: true }]);
  readonly auditTotalPages = signal(0);
  readonly auditFirst = signal(true);
  readonly auditLast = signal(true);

  readonly replayTopic = new FormControl<string>(DEAD_LETTER_TOPICS[0], {nonNullable: true});
  readonly replaying = signal(false);
  readonly lastReplay = signal<DeadLetterReplayResult | null>(null);

  readonly canManageCategories = computed(() =>
    this.auth.permissions().has('NOTIFICATION_CATEGORY_CREATE'),
  );
  readonly canReadCategories = computed(() =>
    this.auth.permissions().has('NOTIFICATION_CATEGORY_READ'),
  );
  readonly canDeleteCategories = computed(() =>
    this.auth.permissions().has('NOTIFICATION_CATEGORY_DELETE'),
  );
  readonly canUpdateCategories = computed(() =>
    this.auth.permissions().has('NOTIFICATION_CATEGORY_UPDATE'),
  );
  readonly canReadAudit = computed(() => this.auth.permissions().has('NOTIFICATION_AUDIT_READ'));
  readonly canReplay = computed(() =>
    this.auth.permissions().has('NOTIFICATION_DEAD_LETTER_REPLAY'),
  );

  readonly topicOptions = computed<SelectOption[]>(() =>
    DEAD_LETTER_TOPICS.map((topic) => ({value: topic, label: topic})),
  );

  ngOnInit(): void {
    if (this.canReadCategories()) {
      this.loadCategories();
    } else {
      this.loadingCategories.set(false);
    }
    if (this.canReadAudit()) {
      this.loadAudit();
    } else {
      this.loadingAudit.set(false);
    }
  }

  async createCategory(): Promise<void> {
    const result = await this.openCategoryDialog(null);
    if (!result) {
      return;
    }
    this.service
      .createCategory({
        code: result.code,
        name: result.name,
        description: result.description,
        mandatory: result.mandatory,
      })
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('admin.notifications.categories.toast.created'));
          this.loadCategories();
        },
        error: (err: AppError) => this.toast.error(err.message),
      });
  }

  async editCategory(category: NotificationCategoryInfo): Promise<void> {
    const result = await this.openCategoryDialog(category);
    if (!result) {
      return;
    }
    this.busyCode.set(category.code);
    this.service
      .updateCategory(category.code, {
        name: result.name,
        description: result.description,
        mandatory: result.mandatory,
      })
      .subscribe({
        next: () => {
          this.busyCode.set(null);
          this.toast.success(this.i18n.translate('admin.notifications.categories.toast.updated'));
          this.loadCategories();
        },
        error: (err: AppError) => {
          this.busyCode.set(null);
          this.toast.error(err.message);
        },
      });
  }

  async deleteCategory(category: NotificationCategoryInfo): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: this.i18n.translate('admin.notifications.categories.delete'),
      message: this.i18n.translate('admin.notifications.categories.deleteMessage', {
        code: category.code,
      }),
      confirmLabel: this.i18n.translate('common.delete'),
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.busyCode.set(category.code);
    this.service.deleteCategory(category.code).subscribe({
      next: () => {
        this.busyCode.set(null);
        this.toast.success(this.i18n.translate('admin.notifications.categories.toast.deleted'));
        this.loadCategories();
      },
      error: (err: AppError) => {
        this.busyCode.set(null);
        this.toast.error(err.message);
      },
    });
  }

  replay(): void {
    this.replaying.set(true);
    this.service.replayDeadLetters(this.replayTopic.value, DEAD_LETTER_BATCH).subscribe({
      next: (result) => {
        this.replaying.set(false);
        this.lastReplay.set(result);
        this.toast.success(
          this.i18n.translate('admin.notifications.deadLetters.toast.replayed', {
            count: result.replayed,
          }),
        );
        if (this.canReadAudit()) {
          this.loadAudit();
        }
      },
      error: (err: AppError) => {
        this.replaying.set(false);
        this.toast.error(err.message);
      },
    });
  }

  onAuditPageChange(page: number): void {
    this.auditPage.set(page);
    this.loadAudit();
  }

  private async openCategoryDialog(
    category: NotificationCategoryInfo | null,
  ): Promise<CategoryDialogResult | undefined> {
    const ref = this.dialog.open<CategoryDialogComponent, CategoryDialogData, CategoryDialogResult | undefined>(
      CategoryDialogComponent,
      {data: {category}},
    );
    return firstValueFrom(ref.closed);
  }

  private loadCategories(): void {
    this.loadingCategories.set(true);
    this.service.categories().subscribe({
      next: (result) => {
        this.categories.set(result.content);
        this.loadingCategories.set(false);
      },
      error: (err: AppError) => {
        this.loadingCategories.set(false);
        this.toast.error(err.message);
      },
    });
  }

  private loadAudit(): void {
    this.loadingAudit.set(true);
    this.service.auditLog(this.auditPage(), AUDIT_PAGE_SIZE).subscribe({
      next: (result) => {
        this.auditEntries.set(result.content);
        this.auditTotalPages.set(result.totalPages);
        this.auditFirst.set(result.first);
        this.auditLast.set(result.last);
        this.loadingAudit.set(false);
      },
      error: (err: AppError) => {
        this.loadingAudit.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
