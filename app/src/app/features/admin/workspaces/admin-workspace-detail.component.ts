import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AppError } from '@core/models/api.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { WorkspaceLogoComponent } from '@features/document-workspace/ui/workspace-logo.component';
import { AdminWorkspaceDetail, AdminWorkspaceService, WorkspaceStatusValue } from './admin-workspace.service';

const MB = 1024 * 1024;
const STATUSES: { value: WorkspaceStatusValue; icon: 'check-circle' | 'eye' | 'lock' }[] = [
  { value: 'ACTIVE', icon: 'check-circle' },
  { value: 'READ_ONLY', icon: 'eye' },
  { value: 'SUSPENDED', icon: 'lock' },
];
const QUOTAS_MB = [0, 1024, 5 * 1024, 10 * 1024, 50 * 1024, 100 * 1024, 500 * 1024, 1024 * 1024];

/** One workspace as a platform administrator sees it: state, limits, people and counters. */
@Component({
  selector: 'app-admin-workspace-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    AvatarComponent,
    IconComponent,
    PageHeaderComponent,
    SpinnerComponent,
    WorkspaceLogoComponent,
    TranslatePipe,
  ],
  templateUrl: './admin-workspace-detail.component.html',
  styleUrl: './admin-workspace-detail.component.scss',
})
export class AdminWorkspaceDetailComponent implements OnInit {
  private readonly service = inject(AdminWorkspaceService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly i18n = inject(TranslationService);

  /** Route param. */
  readonly id = input.required<string>();

  readonly detail = signal<AdminWorkspaceDetail | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly statuses = STATUSES;

  readonly workspace = computed(() => this.detail()?.workspace ?? null);
  readonly usedPercent = computed(() => {
    const ws = this.workspace();
    return ws?.storageQuotaMb ? Math.min(100, (ws.usedBytes / (ws.storageQuotaMb * MB)) * 100) : 0;
  });
  readonly quotaOptions = computed(() => {
    const current = this.workspace()?.storageQuotaMb ?? 0;
    return QUOTAS_MB.includes(current) ? QUOTAS_MB : [...QUOTAS_MB, current].sort((a, b) => a - b);
  });

  ngOnInit(): void {
    this.service.detail(this.id()).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
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

  async setStatus(status: WorkspaceStatusValue): Promise<void> {
    const ws = this.workspace();
    if (!ws || ws.status === status || this.busy()) return;
    if (status === 'SUSPENDED') {
      const ok = await this.dialog.confirm({
        title: this.i18n.translate('admin.workspaces.suspend.title'),
        message: this.i18n.translate('admin.workspaces.suspend.message', { name: ws.name }),
        confirmLabel: this.i18n.translate('admin.workspaces.suspend.confirm'),
        danger: true,
      });
      if (!ok) return;
    }
    this.busy.set(true);
    this.service.setStatus(ws.id, status).subscribe({
      next: (updated) => this.applyWorkspace(updated),
      error: (err: AppError) => this.fail(err),
    });
  }

  setQuota(mb: string): void {
    const ws = this.workspace();
    if (!ws) return;
    this.busy.set(true);
    this.service.setStorageQuota(ws.id, Number(mb)).subscribe({
      next: (updated) => this.applyWorkspace(updated),
      error: (err: AppError) => this.fail(err),
    });
  }

  private applyWorkspace(updated: AdminWorkspaceDetail['workspace']): void {
    this.detail.update((d) => (d ? { ...d, workspace: updated } : d));
    this.busy.set(false);
    this.toast.success(this.i18n.translate('documentWorkspace.toast.changesSaved'));
  }

  private fail(err: AppError): void {
    this.busy.set(false);
    this.toast.error(err.message);
  }
}
