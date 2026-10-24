import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Artifact } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { TrashService } from '../trash/data/trash.service';
import { bindUrlState } from '@shared/utils/url-state';

/** How many rows one page of the list holds. */
const PAGE_SIZE = 30;

/** Trash of one workspace: deleted folders/documents/files, restorable or removable for good. */
@Component({
  selector: 'app-workspace-trash',
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
  ],
  templateUrl: './workspace-trash.component.html',
  styleUrl: './workspace-trash.component.scss',
})
export class WorkspaceTrashComponent implements OnInit {
  private readonly service = inject(TrashService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();

  readonly items = signal<Artifact[]>([]);
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

  typeLabel(item: Artifact): string {
    return this.i18n.translate(`shared.type.${item.type}`);
  }

  ngOnInit(): void {
    this.load();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.listPage(this.documentWorkspaceId(), this.page(), PAGE_SIZE).subscribe({
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

  restore(item: Artifact): void {
    this.busyId.set(item.id);
    this.service.restore(this.documentWorkspaceId(), item.id).subscribe({
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

  async purge(item: Artifact): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('trash.confirmPurge.title'),
      message: this.i18n.translate('wsTrash.confirmPurge.message', { name: item.name }),
      confirmLabel: this.i18n.translate('trash.confirmPurge.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.busyId.set(item.id);
    this.service.purge(this.documentWorkspaceId(), item.id).subscribe({
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

  async emptyTrash(): Promise<void> {
    if (this.items().length === 0) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('trash.confirmEmpty.title'),
      message: this.i18n.translate('trash.confirmEmpty.message'),
      confirmLabel: this.i18n.translate('trash.confirmEmpty.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.service.empty(this.documentWorkspaceId()).subscribe({
      next: () => {
        this.page.set(0);
        this.load();
      },
      error: (err) => this.toast.error(err.message),
    });
  }
}
