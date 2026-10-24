import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { AppError } from '@core/models/api.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { WorkspaceLogoComponent } from '@features/document-workspace/ui/workspace-logo.component';
import { WorkspaceRequestsService } from '@features/document-workspace/data/workspace-extras.service';
import { AdminWorkspace, AdminWorkspaceService, WorkspaceStatusValue } from './admin-workspace.service';
import { bindUrlState } from '@shared/utils/url-state';

const MB = 1024 * 1024;
const STATUSES: WorkspaceStatusValue[] = ['ACTIVE', 'READ_ONLY', 'SUSPENDED'];
/** 0 = no limit. */
const QUOTAS_MB = [0, 1024, 5 * 1024, 10 * 1024, 50 * 1024, 100 * 1024, 500 * 1024, 1024 * 1024];

/** Platform administration of every workspace: its status and its storage limit. */
@Component({
  selector: 'app-admin-workspaces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    EmptyStateComponent,
    IconComponent,
    PageHeaderComponent,
    PaginationComponent,
    SpinnerComponent,
    WorkspaceLogoComponent,
    TranslatePipe,
  ],
  templateUrl: './admin-workspaces.component.html',
  styleUrl: './admin-workspaces.component.scss',
})
export class AdminWorkspacesComponent implements OnInit {
  private readonly service = inject(AdminWorkspaceService);
  private readonly requests = inject(WorkspaceRequestsService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly i18n = inject(TranslationService);

  readonly statuses = STATUSES;
  /** Storage requests waiting for an answer, shown next to the link to their page. */
  readonly pendingRequests = signal(0);
  readonly items = signal<AdminWorkspace[]>([]);
  readonly loading = signal(true);
  readonly search = signal('');
  readonly statusFilter = signal<WorkspaceStatusValue | ''>('');
  readonly page = signal(0);

  /** The address and this state are one: filters, order and page are read from it and kept in it. */
  private readonly urlState = bindUrlState([
    { signal: this.search, param: 'q' },
    { signal: this.statusFilter, param: 'status' },
    { signal: this.page, param: 'page', page: true },
  ]);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  private readonly search$ = new Subject<string>();

  ngOnInit(): void {
    this.requests.pendingStorageCount().subscribe({ next: (r) => this.pendingRequests.set(r.count), error: () => undefined });
    this.search$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((value) => {
      this.search.set(value);
      this.page.set(0);
      this.load();
    });
    this.load();
  }

  onSearch(value: string): void {
    this.search$.next(value);
  }

  setStatusFilter(value: string): void {
    this.statusFilter.set(value as WorkspaceStatusValue | '');
    this.page.set(0);
    this.load();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.list({ search: this.search(), status: this.statusFilter(), page: this.page() }).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalPages.set(result.totalPages);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  quotaOptions(current: number | null): number[] {
    const value = current ?? 0;
    return QUOTAS_MB.includes(value) ? QUOTAS_MB : [...QUOTAS_MB, value].sort((a, b) => a - b);
  }

  quotaLabel(mb: number): string {
    return mb === 0 ? this.i18n.translate('documentWorkspace.settings.storage.unlimited') : this.formatBytes(mb * MB);
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    const units = ['KB', 'MB', 'GB', 'TB'];
    let value = bytes / 1024;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit++;
    }
    return `${value >= 10 || Number.isInteger(value) ? Math.round(value) : value.toFixed(1)} ${units[unit]}`;
  }

  async changeStatus(workspace: AdminWorkspace, value: string): Promise<void> {
    const status = value as WorkspaceStatusValue;
    if (status === workspace.status) return;
    if (status === 'SUSPENDED') {
      const ok = await this.dialog.confirm({
        title: this.i18n.translate('admin.workspaces.suspend.title'),
        message: this.i18n.translate('admin.workspaces.suspend.message', { name: workspace.name }),
        confirmLabel: this.i18n.translate('admin.workspaces.suspend.confirm'),
        danger: true,
      });
      if (!ok) {
        this.items.update((list) => [...list]);
        return;
      }
    }
    this.service.setStatus(workspace.id, status).subscribe({
      next: (updated) => this.replace(updated),
      error: (err: AppError) => {
        this.toast.error(err.message);
        this.items.update((list) => [...list]);
      },
    });
  }

  changeQuota(workspace: AdminWorkspace, mb: string): void {
    this.service.setStorageQuota(workspace.id, Number(mb)).subscribe({
      next: (updated) => this.replace(updated),
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  private replace(updated: AdminWorkspace): void {
    this.items.update((list) => list.map((w) => (w.id === updated.id ? updated : w)));
    this.toast.success(this.i18n.translate('documentWorkspace.toast.changesSaved'));
  }
}
