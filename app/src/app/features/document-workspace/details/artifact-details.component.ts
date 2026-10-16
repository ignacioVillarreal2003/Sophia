import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AppError } from '@core/models/api.model';
import {
  AclEntry,
  Artifact,
  BreadcrumbEntry,
  DocumentWorkspaceMember,
  KIND_BY_TYPE,
  UpdateArtifactRequest,
} from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ArtifactService } from '../data/artifact.service';
import { PermissionService } from '../data/permission.service';
import { MoveDialogComponent, MoveDialogData, MoveDialogResult } from '../ui/move-dialog.component';
import { ShareDialogComponent, ShareDialogData } from '../ui/share-dialog.component';
import { WORKSPACE_COLORS } from '../workspace-colors';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';


/**
 * Properties of one folder/file: every editable property, who it is shared with,
 * and the actions that apply to it (copy, move, trash).
 */
@Component({
  selector: 'app-artifact-details',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WorkspaceDatePipe,
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    AvatarComponent,
    TextFieldComponent,
    TextareaComponent,
    IconComponent,
    PageHeaderComponent,
    SpinnerComponent,
    TranslatePipe,
  ],
  templateUrl: './artifact-details.component.html',
  styleUrl: './artifact-details.component.scss',
})
export class ArtifactDetailsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly artifacts = inject(ArtifactService);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly permissions = inject(PermissionService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route params. */
  readonly documentWorkspaceId = input.required<string>();
  readonly artifactId = input.required<string>();

  readonly artifact = signal<Artifact | null>(null);
  readonly path = signal<BreadcrumbEntry[]>([]);
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly access = signal<AclEntry[]>([]);
  readonly itemCount = signal<number | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly busy = signal<'copy' | 'move' | 'trash' | null>(null);

  readonly swatches = WORKSPACE_COLORS;

  readonly icon = computed(() => artifactIcon(this.artifact()?.type ?? 'FOLDER'));
  readonly type = computed(() => this.artifact()?.type ?? 'FOLDER');
  readonly isFolder = computed(() => this.type() === 'FOLDER');
  readonly isDocument = computed(() => KIND_BY_TYPE[this.type()] === 'DOCUMENT');

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    description: [''],
    color: [''],
  });
  readonly draft = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly color = computed(() => this.draft().color || 'var(--text-muted)');

  readonly location = computed(() => {
    const root = this.workspaces.active()?.name ?? this.i18n.translate('documents.home');
    // The endpoint lists ancestors only, but tolerate it including the item itself.
    const parents = this.path().filter((p) => p.id !== this.artifact()?.id).map((p) => p.name);
    return [root, ...parents].join(' / ');
  });

  readonly owner = computed(() => {
    const a = this.artifact();
    return a ? this.members().find((m) => m.userId === a.ownerId) ?? null : null;
  });

  /** People this item was explicitly shared with, resolved to workspace members. */
  readonly sharedWith = computed(() =>
    this.access().map((entry) => {
      const member = this.members().find((m) => m.userId === entry.subjectUserId);
      return {
        id: entry.id,
        name: member?.fullName || member?.email || this.i18n.translate('share.fallbackUser'),
        photoUrl: member?.photoUrl ?? null,
        modes: entry.modes.map((m) => this.i18n.translate(`share.mode.${m}`)).join(' · '),
      };
    }),
  );

  readonly dirty = computed(() => {
    const a = this.artifact();
    if (!a) return false;
    const v = this.draft();
    return (
      v.name !== a.name ||
      (v.description ?? '') !== (a.description ?? '') ||
      (this.isFolder() && (v.color ?? '') !== (a.color ?? '')) ||
      false
    );
  });

  ngOnInit(): void {
    const ws = this.documentWorkspaceId();
    const id = this.artifactId();
    this.artifacts.get(ws, id).subscribe({
      next: (a) => {
        this.artifact.set(a);
        this.patchForm(a);
        this.loading.set(false);
        if (a.type === 'FOLDER') {
          this.artifacts.list(ws, a.id, 0, 1).subscribe({ next: (page) => this.itemCount.set(page.totalElements) });
        }
      },
      error: () => this.loading.set(false),
    });
    this.artifacts.breadcrumb(ws, id).subscribe({ next: (p) => this.path.set(p), error: () => this.path.set([]) });
    this.workspaces.members(ws).subscribe({ next: (list) => this.members.set(list) });
    this.loadAccess();
  }

  private loadAccess(): void {
    this.permissions.list(this.documentWorkspaceId(), this.artifactId()).subscribe({
      next: (list) => this.access.set(list),
      error: () => this.access.set([]),
    });
  }

  private patchForm(a: Artifact): void {
    this.form.setValue({
      name: a.name,
      description: a.description ?? '',
      color: a.color ?? '',
    });
  }

  typeLabel(type: string): string {
    const key = `newItem.type.${type}`;
    const label = this.i18n.translate(key);
    if (label !== key) return label;
    const text = type.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  setColor(color: string): void {
    this.form.controls.color.setValue(this.form.controls.color.value === color ? '' : color);
  }

  back(): void {
    const parent = this.artifact()?.parentId;
    const base = ['/document-workspaces', this.documentWorkspaceId()];
    this.router.navigate(parent ? [...base, 'folder', parent] : base);
  }

  // ---- save ----
  save(): void {
    const a = this.artifact();
    if (!a || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: UpdateArtifactRequest = {
      name: v.name.trim(),
      description: v.description.trim(),
      ...(this.isFolder() ? { color: v.color } : {}),
    };

    this.saving.set(true);
    this.artifacts.update(this.documentWorkspaceId(), a.id, request).subscribe({
      next: (updated) => this.saved(updated),
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  private saved(updated: Artifact): void {
    this.artifact.set(updated);
    this.patchForm(updated);
    this.saving.set(false);
    this.toast.success(this.i18n.translate('documentWorkspace.toast.changesSaved'));
  }

  // ---- sharing ----
  async manageAccess(): Promise<void> {
    const ref = this.dialog.open<ShareDialogComponent, ShareDialogData>(ShareDialogComponent, {
      data: { documentWorkspaceId: this.documentWorkspaceId(), artifactId: this.artifactId() },
    });
    await firstValueFrom(ref.closed, { defaultValue: undefined });
    this.loadAccess();
  }

  // ---- actions ----
  copy(): void {
    const a = this.artifact();
    if (!a) return;
    this.busy.set('copy');
    this.artifacts.duplicate(this.documentWorkspaceId(), a.id).subscribe({
      next: (copy) => {
        this.busy.set(null);
        this.toast.success(this.i18n.translate('explorer.toast.duplicated'));
        this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'details', copy.id]);
      },
      error: (err: AppError) => {
        this.busy.set(null);
        this.toast.error(err.message);
      },
    });
  }

  async move(): Promise<void> {
    const a = this.artifact();
    if (!a) return;
    const ref = this.dialog.open<MoveDialogComponent, MoveDialogData, MoveDialogResult>(MoveDialogComponent, {
      data: { documentWorkspaceId: this.documentWorkspaceId(), artifact: a },
    });
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!result) return;
    this.busy.set('move');
    this.artifacts.move(this.documentWorkspaceId(), a.id, { newParentId: result.parentId }).subscribe({
      next: (moved) => {
        this.busy.set(null);
        this.artifact.set(moved);
        this.artifacts
          .breadcrumb(this.documentWorkspaceId(), a.id)
          .subscribe({ next: (p) => this.path.set(p), error: () => this.path.set([]) });
        this.toast.success(this.i18n.translate('explorer.toast.moved'));
      },
      error: (err: AppError) => {
        this.busy.set(null);
        this.toast.error(err.message);
      },
    });
  }

  async trash(): Promise<void> {
    const a = this.artifact();
    if (!a) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('explorer.menu.trash'),
      message: this.i18n.translate('details.actions.trash.confirm', { name: a.name }),
      confirmLabel: this.i18n.translate('explorer.menu.trash'),
      danger: true,
    });
    if (!ok) return;
    this.busy.set('trash');
    this.artifacts.trash(this.documentWorkspaceId(), a.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('explorer.toast.trashed'));
        this.back();
      },
      error: (err: AppError) => {
        this.busy.set(null);
        this.toast.error(err.message);
      },
    });
  }
}
