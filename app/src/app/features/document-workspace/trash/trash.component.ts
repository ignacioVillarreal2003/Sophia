import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DocumentWorkspace } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

/** Deleted document workspaces (not their content): restore or delete for good. */
@Component({
  selector: 'app-trash',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    RelativeTimePipe,
    TranslatePipe,
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
