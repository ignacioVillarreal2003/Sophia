import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DocumentWorkspace } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { WorkspaceLogoComponent } from '../ui/workspace-logo.component';
import { bindUrlState } from '@shared/utils/url-state';

/** Deleted document workspaces (not their content): restore or delete for good. */
@Component({
  selector: 'app-trash',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ButtonComponent,
    DatePipe,
    SpinnerComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    RelativeTimePipe,
    TranslatePipe,
    WorkspaceLogoComponent,
  ],
  templateUrl: './trash.component.html',
  styleUrl: './trash.component.scss',
})
export class TrashComponent implements OnInit {
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly items = signal<DocumentWorkspace[]>([]);
  readonly loading = signal(true);
  readonly busyId = signal<string | null>(null);
  readonly page = signal(0);

  /** The address and this state are one: filters, order and page are read from it and kept in it. */
  private readonly urlState = bindUrlState([
    { signal: this.page, param: 'page', page: true },
  ]);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  ngOnInit(): void {
    this.load();
  }

  /** Whole days before the workspace is erased for good; null when the server did not say. */
  daysLeft(item: DocumentWorkspace): number | null {
    if (!item.purgeAt) return null;
    return Math.max(0, Math.ceil((new Date(item.purgeAt).getTime() - Date.now()) / 86_400_000));
  }

  /** "Deleted for good in 12 days": what is left of the retention period, in words. */
  leftText(item: DocumentWorkspace): string {
    const days = this.daysLeft(item);
    if (days !== null && days > 0) {
      return this.i18n.translate('trash.expiresIn', { days });
    }
    return item.purgeAt ? this.i18n.translate('trash.expiresToday') : '';
  }

  /** Grey while there is plenty of time, amber in the last week, red in the last three days. */
  levelColor(item: DocumentWorkspace): string {
    const days = this.daysLeft(item);
    if (days === null) return 'var(--text-subtle)';
    if (days <= 3) return '#d92d20';
    return days <= 7 ? '#b54708' : 'var(--text-subtle)';
  }

  /** Close enough to the end that the owner should act now. */
  isUrgent(item: DocumentWorkspace): boolean {
    const days = this.daysLeft(item);
    return days !== null && days <= 3;
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.documentWorkspaces.listDeleted(this.page()).subscribe({
      next: (result) => {
        // The last row of a page can disappear (restore/purge); step back instead of showing an empty page.
        if (result.content.length === 0 && result.totalPages > 0 && this.page() > 0) {
          this.page.set(result.totalPages - 1);
          this.load();
          return;
        }
        this.items.set(result.content);
        this.totalPages.set(result.totalPages);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  restore(item: DocumentWorkspace): void {
    this.busyId.set(item.id);
    this.documentWorkspaces.restore(item.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success(this.i18n.translate('trash.toast.restored'));
        this.load();
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(err.message);
      },
    });
  }

  async purge(item: DocumentWorkspace): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('trash.confirmPurge.title'),
      message: this.i18n.translate('trash.confirmPurge.message', { name: item.name }),
      confirmLabel: this.i18n.translate('trash.confirmPurge.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.busyId.set(item.id);
    this.documentWorkspaces.purge(item.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.load();
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(err.message);
      },
    });
  }
}
