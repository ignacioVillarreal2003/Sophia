import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DocumentWorkspace, DocumentWorkspaceMember, StorageRequest, WorkspaceUsage } from '@core/models/document-workspace';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { AppError } from '@core/models/api.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SelectFieldComponent, SelectOption } from '@shared/ui/form-field/select-field.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { WORKSPACE_PATTERNS, patternFor } from '../workspace-patterns';
import { WorkspaceLogoComponent } from '../ui/workspace-logo.component';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';
import { WorkspaceRequestsService } from '../data/workspace-extras.service';
import { DeleteWorkspaceDialogComponent, DeleteWorkspaceData } from '../list-dialogs/delete-workspace-dialog.component';
import { TransferOwnershipDialogComponent, TransferOwnershipData } from '../list-dialogs/transfer-ownership-dialog.component';
import { StorageRequestDialogComponent, StorageRequestData } from '../list-dialogs/storage-request-dialog.component';
import { DuplicateWorkspaceDialogComponent, DuplicateWorkspaceData } from '../list-dialogs/duplicate-workspace-dialog.component';

const MB = 1024 * 1024;
const LANGUAGES: { code: string; label: string }[] = [
  { code: 'es', label: 'Español' },
  { code: 'en', label: 'English' },
  { code: 'pt', label: 'Português' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' },
  { code: 'it', label: 'Italiano' },
];
/** `0` means "no override": back to the system default retention. */
const RETENTION_DAYS = [0, 7, 14, 30, 60, 90, 180, 365];
const DATE_FORMATS = [
  { value: 'yyyy-MM-dd', example: '2026-10-14' },
  { value: 'dd/MM/yyyy', example: '14/10/2026' },
  { value: 'MM/dd/yyyy', example: '10/14/2026' },
];
const MAX_LOGO_BYTES = 2 * MB;

@Component({
  selector: 'app-document-workspace-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    IconComponent,
    SelectFieldComponent,
    TextFieldComponent,
    TextareaComponent,
    PageHeaderComponent,
    SpinnerComponent,
    WorkspaceLogoComponent,
    WorkspaceDatePipe,
    TranslatePipe,
  ],
  templateUrl: './document-workspace-settings.component.html',
  styleUrl: './document-workspace-settings.component.scss',
})
export class DocumentWorkspaceSettingsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DocumentWorkspaceService);
  private readonly auth = inject(AuthenticationService);
  private readonly requests = inject(WorkspaceRequestsService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly documentWorkspaceId = input.required<string>();

  readonly documentWorkspace = signal<DocumentWorkspace | null>(null);
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly usage = signal<WorkspaceUsage | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly logoBusy = signal(false);

  readonly patterns = WORKSPACE_PATTERNS;
  readonly languageOptions: SelectOption[] = LANGUAGES.map((l) => ({ value: l.code, label: l.label }));
  readonly dateFormats = DATE_FORMATS;
  private readonly zones = this.loadTimezones();
  /** Public links currently working in this workspace (shown next to "stop sharing everything"). */

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
    color: [WORKSPACE_PATTERNS[0].id],
    language: ['es'],
  });
  readonly draft = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  /** The mark the form holds: a pattern id. */
  readonly color = computed(() => this.draft().color || WORKSPACE_PATTERNS[0].id);


  readonly retentionDays = computed(() => this.documentWorkspace()?.trashRetentionDays ?? 0);
  readonly timezone = computed(() => this.documentWorkspace()?.timezone ?? 'UTC');
  readonly dateFormat = computed(() => this.documentWorkspace()?.dateFormat ?? 'yyyy-MM-dd');

  /** Only the owner and the admins may change how the workspace is set up; everyone else reads it. */
  readonly canManage = computed(() => {
    const ws = this.documentWorkspace();
    return !!ws && (ws.ownerId === this.auth.user()?.id || ws.myRole === 'ADMIN');
  });

  readonly isOwner = computed(() => !!this.documentWorkspace() && this.documentWorkspace()!.ownerId === this.auth.user()?.id);
  /** The owner's latest request for more storage, to show whether it is still waiting. */
  readonly storageRequest = signal<StorageRequest | null>(null);
  readonly backupBusy = signal(false);
  readonly storageState = computed<'ok' | 'warn' | 'full'>(() => {
    const percent = this.usedPercent();
    return percent >= 100 ? 'full' : percent >= 80 ? 'warn' : 'ok';
  });

  readonly owner = computed(() => this.members().find((m) => m.userId === this.documentWorkspace()?.ownerId) ?? null);

  readonly updatedBy = computed(() => this.members().find((m) => m.userId === this.documentWorkspace()?.updatedBy) ?? null);

  /** The preset list, plus whatever value is currently stored when it is not one of them. */
  /** The zones the browser knows, plus the one stored when it is not among them (UTC, for one). */
  readonly timezoneOptions = computed<SelectOption[]>(() => {
    const current = this.timezone();
    const all = this.zones.includes(current) ? this.zones : [current, ...this.zones];
    return all.map((zone) => ({ value: zone, label: zone }));
  });

  readonly retentionOptions = computed(() => this.withCurrent(RETENTION_DAYS, this.retentionDays()));
  readonly retentionSelect = computed<SelectOption[]>(() =>
    this.retentionOptions().map((days) => ({ value: String(days), label: this.retentionLabel(days) })),
  );

  readonly usedPercent = computed(() => {
    const usage = this.usage();
    if (!usage?.quotaMb) return 0;
    return Math.min(100, (usage.usedBytes / (usage.quotaMb * MB)) * 100);
  });

  readonly dirty = computed(() => {
    const ws = this.documentWorkspace();
    const v = this.draft();
    return (
      !!ws &&
      (v.name !== ws.name ||
        (v.description ?? '') !== (ws.description ?? '') ||
        (v.color ?? '') !== patternFor(ws.color, ws.id).id ||
        (v.language ?? '') !== (ws.language ?? 'es'))
    );
  });

  ngOnInit(): void {
    const id = this.documentWorkspaceId();
    this.service.get(id).subscribe({
      next: (ws) => {
        this.documentWorkspace.set(ws);
        this.patchForm(ws);
        if (!this.canManage()) {
          this.form.disable({ emitEvent: false });
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.service.members(id).subscribe({ next: (list) => this.members.set(list) });
    this.refreshStorageRequest();
    this.refreshUsage();
  }

  private patchForm(ws: DocumentWorkspace): void {
    this.form.setValue({
      name: ws.name,
      description: ws.description ?? '',
      color: patternFor(ws.color, ws.id).id,
      language: ws.language ?? 'es',
    });
  }

  private refreshUsage(): void {
    this.service.usage(this.documentWorkspaceId()).subscribe({ next: (u) => this.usage.set(u), error: () => this.usage.set(null) });
  }

  private loadTimezones(): string[] {
    const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
    const all = typeof intl.supportedValuesOf === 'function' ? intl.supportedValuesOf('timeZone') : [];
    return all.length ? all : ['UTC', 'America/Montevideo', 'America/Argentina/Buenos_Aires', 'America/Sao_Paulo', 'America/New_York', 'Europe/Madrid', 'Europe/London'];
  }

  private withCurrent(options: number[], current: number): number[] {
    return options.includes(current) ? options : [...options, current].sort((a, b) => a - b);
  }

  // ---- labels ----
  retentionLabel(days: number): string {
    return days === 0
      ? this.i18n.translate('documentWorkspace.settings.retention.default')
      : this.i18n.translate('documentWorkspace.settings.retention.days', { count: days });
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

  // ---- general ----
  setColor(color: string): void {
    this.form.controls.color.setValue(color);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.service.update(this.documentWorkspaceId(), this.form.getRawValue()).subscribe({
      next: (ws) => {
        this.documentWorkspace.set(ws);
        this.patchForm(ws);
        this.saving.set(false);
        this.toast.success(this.i18n.translate('documentWorkspace.toast.changesSaved'));
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  // ---- logo ----
  onLogoPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type)) {
      this.toast.error(this.i18n.translate('documentWorkspace.settings.logo.invalidType'));
      return;
    }
    if (file.size > MAX_LOGO_BYTES) {
      this.toast.error(this.i18n.translate('documentWorkspace.settings.logo.tooLarge'));
      return;
    }
    this.logoBusy.set(true);
    this.service.uploadLogo(this.documentWorkspaceId(), file).subscribe({
      next: (ws) => this.logoChanged(ws),
      error: (err: AppError) => {
        this.logoBusy.set(false);
        this.toast.error(err.message);
      },
    });
  }

  removeLogo(): void {
    this.logoBusy.set(true);
    this.service.removeLogo(this.documentWorkspaceId()).subscribe({
      next: (ws) => this.logoChanged(ws),
      error: (err: AppError) => {
        this.logoBusy.set(false);
        this.toast.error(err.message);
      },
    });
  }

  private logoChanged(ws: DocumentWorkspace): void {
    this.documentWorkspace.set(ws);
    this.logoBusy.set(false);
    this.toast.success(this.i18n.translate('documentWorkspace.toast.changesSaved'));
  }

  // ---- settings that save as soon as they change ----
  private patchSettings(patch: Record<string, unknown>): void {
    this.service.updateSettings(this.documentWorkspaceId(), patch).subscribe({
      next: (ws) => {
        this.documentWorkspace.set(ws);
        this.toast.success(this.i18n.translate('documentWorkspace.toast.changesSaved'));
        this.refreshUsage();
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  setRetention(days: string): void {
    this.patchSettings({ trashRetentionDays: Number(days) });
  }

  setTimezone(zone: string): void {
    this.patchSettings({ timezone: zone });
  }

  setDateFormat(format: string): void {
    this.patchSettings({ dateFormat: format });
  }

  // ---- backup, storage, ownership ----
  downloadBackup(): void {
    const ws = this.documentWorkspace();
    if (!ws || this.backupBusy()) return;
    this.backupBusy.set(true);
    this.service.downloadArchive(ws.id, ws.name).subscribe({
      next: () => this.backupBusy.set(false),
      error: (err: AppError) => {
        this.backupBusy.set(false);
        this.toast.error(err.message);
      },
    });
  }

  private refreshStorageRequest(): void {
    this.requests.latestStorageRequest(this.documentWorkspaceId()).subscribe({
      next: (request) => this.storageRequest.set(request),
      error: () => this.storageRequest.set(null),
    });
  }

  requestStorage(): void {
    const ws = this.documentWorkspace();
    if (!ws) return;
    const usage = this.usage();
    const ref = this.dialog.open<StorageRequestDialogComponent, StorageRequestData, boolean>(StorageRequestDialogComponent, {
      data: {
        workspace: {
          ...ws,
          stats: { memberCount: this.members().length, itemCount: 0, usedBytes: usage?.usedBytes ?? 0, quotaMb: usage?.quotaMb ?? null, members: [] },
        },
      },
    });
    ref.closed.subscribe((sent) => {
      if (!sent) return;
      this.toast.success(this.i18n.translate('workspaces.toast.storageRequested'));
      this.refreshStorageRequest();
    });
  }

  transferOwnership(): void {
    const ws = this.documentWorkspace();
    if (!ws) return;
    const ref = this.dialog.open<TransferOwnershipDialogComponent, TransferOwnershipData, boolean>(TransferOwnershipDialogComponent, {
      data: { workspace: ws },
    });
    ref.closed.subscribe((done) => {
      if (!done) return;
      this.toast.success(this.i18n.translate('workspaces.toast.transferred', { name: ws.name }));
      this.service.get(ws.id).subscribe((updated) => this.documentWorkspace.set(updated));
      this.service.members(ws.id).subscribe({ next: (list) => this.members.set(list) });
    });
  }

  // ---- copies and leaving ----
  duplicate(): void {
    const ws = this.documentWorkspace();
    if (!ws) return;
    const ref = this.dialog.open<DuplicateWorkspaceDialogComponent, DuplicateWorkspaceData, DocumentWorkspace | undefined>(
      DuplicateWorkspaceDialogComponent,
      { data: { workspace: ws } },
    );
    ref.closed.subscribe((copy) => {
      if (!copy) return;
      this.toast.success(this.i18n.translate('workspaces.toast.duplicated', { name: copy.name }), {
        action: { label: this.i18n.translate('common.open'), run: () => this.router.navigate(['/document-workspaces', copy.id]) },
      });
    });
  }

  async leave(): Promise<void> {
    const ws = this.documentWorkspace();
    if (!ws) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('workspaces.leave.title'),
      message: this.i18n.translate('workspaces.leave.message', { name: ws.name }),
      confirmLabel: this.i18n.translate('workspaces.leave.confirm'),
      danger: true,
    });
    if (!ok) return;
    this.service.leave(ws.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('workspaces.toast.left', { name: ws.name }));
        this.router.navigate(['/document-workspaces']);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  // ---- delete ----
  remove(): void {
    const ws = this.documentWorkspace();
    if (!ws) return;
    const ref = this.dialog.open<DeleteWorkspaceDialogComponent, DeleteWorkspaceData, boolean>(DeleteWorkspaceDialogComponent, {
      data: { workspace: ws },
    });
    ref.closed.subscribe((done) => {
      if (!done) return;
      this.toast.success(this.i18n.translate('documentWorkspace.toast.deleted'));
      this.router.navigate(['/document-workspaces']);
    });
  }
}
