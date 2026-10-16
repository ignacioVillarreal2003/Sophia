import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DocumentWorkspace, DocumentWorkspaceMember, WorkspaceUsage } from '@core/models/document-workspace';
import { AppError } from '@core/models/api.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { WORKSPACE_COLORS } from '../workspace-colors';
import { WorkspaceLogoComponent } from '../ui/workspace-logo.component';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';
import { PublicLinkService } from '../data/public-link.service';

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
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    IconComponent,
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
  private readonly publicLinks = inject(PublicLinkService);
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

  readonly swatches = WORKSPACE_COLORS;
  readonly languages = LANGUAGES;
  readonly dateFormats = DATE_FORMATS;
  readonly timezones = this.loadTimezones();
  /** Public links currently working in this workspace (shown next to "stop sharing everything"). */
  readonly activeLinks = signal(0);

  /** How the workspace home lists items; a per-user preference shared by every workspace. */
  readonly view = signal<'grid' | 'list'>(this.readView());

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
    color: [WORKSPACE_COLORS[0]],
    language: ['es'],
  });
  readonly draft = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly color = computed(() => this.draft().color || WORKSPACE_COLORS[0]);

  readonly retentionDays = computed(() => this.documentWorkspace()?.trashRetentionDays ?? 0);
  readonly publicSharing = computed(() => this.documentWorkspace()?.publicSharingEnabled === true);
  readonly timezone = computed(() => this.documentWorkspace()?.timezone ?? 'UTC');
  readonly dateFormat = computed(() => this.documentWorkspace()?.dateFormat ?? 'yyyy-MM-dd');

  readonly owner = computed(() => this.members().find((m) => m.userId === this.documentWorkspace()?.ownerId) ?? null);

  readonly updatedBy = computed(() => this.members().find((m) => m.userId === this.documentWorkspace()?.updatedBy) ?? null);

  /** The preset list, plus whatever value is currently stored when it is not one of them. */
  readonly retentionOptions = computed(() => this.withCurrent(RETENTION_DAYS, this.retentionDays()));

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
        (v.color ?? '').toLowerCase() !== (ws.color ?? WORKSPACE_COLORS[0]).toLowerCase() ||
        (v.language ?? '') !== (ws.language ?? 'es'))
    );
  });

  ngOnInit(): void {
    const id = this.documentWorkspaceId();
    this.service.get(id).subscribe({
      next: (ws) => {
        this.documentWorkspace.set(ws);
        this.patchForm(ws);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.service.members(id).subscribe({ next: (list) => this.members.set(list) });
    this.refreshUsage();
    this.refreshLinks();
  }

  private patchForm(ws: DocumentWorkspace): void {
    this.form.setValue({
      name: ws.name,
      description: ws.description ?? '',
      color: ws.color ?? WORKSPACE_COLORS[0],
      language: ws.language ?? 'es',
    });
  }

  private refreshUsage(): void {
    this.service.usage(this.documentWorkspaceId()).subscribe({ next: (u) => this.usage.set(u), error: () => this.usage.set(null) });
  }

  private refreshLinks(): void {
    this.publicLinks.activeCount(this.documentWorkspaceId()).subscribe({
      next: (n) => this.activeLinks.set(n),
      error: () => this.activeLinks.set(0),
    });
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

  setPublicSharing(enabled: boolean): void {
    this.patchSettings({ publicSharingEnabled: enabled });
  }

  /** "Stop sharing everything": revoke every public link of the workspace. */
  async stopSharingAll(): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('documentWorkspace.settings.sharing.stop.title'),
      message: this.i18n.translate('documentWorkspace.settings.sharing.stop.message', { count: this.activeLinks() }),
      confirmLabel: this.i18n.translate('documentWorkspace.settings.sharing.stop.confirm'),
      danger: true,
    });
    if (!ok) return;
    this.publicLinks.revokeAll(this.documentWorkspaceId()).subscribe({
      next: (revoked) => {
        this.activeLinks.set(0);
        this.toast.success(this.i18n.translate('documentWorkspace.settings.sharing.stop.done', { count: revoked }));
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  // ---- view preference ----
  setView(view: 'grid' | 'list'): void {
    this.view.set(view);
    try {
      localStorage.setItem('sophia.explorer.view', view);
    } catch {
      // Preference only; ignore storage failures.
    }
  }

  private readView(): 'grid' | 'list' {
    try {
      return localStorage.getItem('sophia.explorer.view') === 'list' ? 'list' : 'grid';
    } catch {
      return 'grid';
    }
  }

  // ---- delete ----
  async remove(): Promise<void> {
    const ws = this.documentWorkspace();
    if (!ws) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('documentWorkspace.confirmDelete.title'),
      message: this.i18n.translate('documentWorkspace.confirmDelete.message', { name: ws.name }),
      confirmLabel: this.i18n.translate('documentWorkspace.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.service.remove(this.documentWorkspaceId()).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('documentWorkspace.toast.deleted'));
        this.router.navigate(['/']);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }
}
