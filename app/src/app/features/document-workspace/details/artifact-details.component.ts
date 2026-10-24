import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AppError } from '@core/models/api.model';
import {
  Artifact,
  BreadcrumbEntry,
  DocumentWorkspaceMember,
  ExportFormat,
  KIND_BY_TYPE,
  Tag,
  UpdateArtifactRequest,
} from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { artifactTone } from '@shared/utils/artifact-tone';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ArtifactService } from '../data/artifact.service';
import { ExportImportService } from '../data/export-import.service';
import { FileService } from '../data/file.service';
import { WorkspaceActivityComponent } from '../activity/workspace-activity.component';
import { CommentThreadComponent } from '../comments/comment-thread.component';
import { RevisionHistoryComponent } from '../revisions/revision-history.component';
import { TagService } from '../data/tag.service';
import { MoveDialogComponent, MoveDialogData, MoveDialogResult } from '../ui/move-dialog.component';
import { WORKSPACE_COLORS } from '../workspace-colors';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';


type DetailsTab = 'general' | 'comments' | 'history' | 'activity' | 'ai' | 'download';

/**
 * Properties of one folder/file: every editable property, who it is shared with,
 * and the actions that apply to it (copy, move, trash).
 */
@Component({
  selector: 'app-artifact-details',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommentThreadComponent,
    RevisionHistoryComponent,
    WorkspaceActivityComponent,
    WorkspaceDatePipe,
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    TextFieldComponent,
    TextareaComponent,
    IconComponent,
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
  private readonly tagService = inject(TagService);
  private readonly exportImport = inject(ExportImportService);
  private readonly files = inject(FileService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route params. */
  readonly documentWorkspaceId = input.required<string>();
  readonly artifactId = input.required<string>();
  /** Which section to open on arrival (?tab=comments, ...). */
  readonly tabParam = input<string | undefined>(undefined, { alias: 'tab' });

  readonly artifact = signal<Artifact | null>(null);
  readonly path = signal<BreadcrumbEntry[]>([]);
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly itemCount = signal<number | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly busy = signal<'copy' | 'move' | 'trash' | null>(null);

  readonly swatches = WORKSPACE_COLORS;

  /** The sections there are for this item: a folder has no comments, history, AI or downloads. */
  readonly tabs = computed<DetailsTab[]>(() =>
    this.isFolder()
      ? ['general', 'activity']
      : this.isDocument()
        ? ['general', 'comments', 'history', 'activity', 'ai', 'download']
        : ['general', 'activity', 'ai', 'download'],
  );
  private readonly chosen = signal<DetailsTab | null>(null);
  /** The section on show: the one chosen, else the one asked for in the link, else General. */
  readonly tab = computed<DetailsTab>(() => {
    const asked = this.chosen() ?? this.tabParam();
    const wanted = (asked === 'share' || asked === 'sharing' ? 'download' : asked) as DetailsTab | undefined;
    return wanted && this.tabs().includes(wanted) ? wanted : 'general';
  });

  selectTab(tab: DetailsTab): void {
    this.chosen.set(tab);
    void this.router.navigate([], { queryParams: { tab: tab === 'general' ? null : tab }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  onRestored(): void {
    this.toast.success(this.i18n.translate('documentView.versionRestored'));
  }

  readonly icon = computed(() => artifactIcon(this.artifact()?.type ?? 'FOLDER'));
  readonly type = computed(() => this.artifact()?.type ?? 'FOLDER');
  readonly isFolder = computed(() => this.type() === 'FOLDER');
  readonly isDocument = computed(() => KIND_BY_TYPE[this.type()] === 'DOCUMENT');

  /** The formats this kind of document can be downloaded as; none for an uploaded file, which downloads as it is. */
  readonly exportFormats = computed<ExportFormat[]>(() => {
    switch (this.type()) {
      case 'SPREADSHEET':
        return ['XLSX', 'CSV', 'PDF', 'JSON'];
      case 'PRESENTATION':
        return ['PPTX', 'MARKDOWN', 'PDF', 'HTML', 'JSON'];
      case 'WHITEBOARD':
        return ['JSON', 'MARKDOWN', 'PDF'];
      case 'MARKDOWN_DOCUMENT':
      case 'CHECKLIST':
        return ['MARKDOWN', 'HTML', 'PDF', 'DOCX', 'TXT'];
      default:
        return [];
    }
  });

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    description: [''],
    color: [''],
  });
  readonly draft = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly color = computed(() => this.draft().color || artifactTone(this.type()));

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
    this.loadTags();
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

  formatLabel(format: ExportFormat): string {
    return format === 'MARKDOWN' ? 'Markdown' : format === 'XLSX' ? 'Excel' : format === 'DOCX' ? 'Word' : format === 'PPTX' ? 'PowerPoint' : format;
  }

  /** Opens the document itself, optionally with one of its side panels already open. */
  openDocument(panel?: 'comments' | 'history' | 'ai'): void {
    void this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'a', this.artifactId()], {
      queryParams: panel ? { panel } : {},
    });
  }

  openActivity(): void {
    void this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'activity']);
  }

  openAi(): void {
    if (this.type() === 'MARKDOWN_DOCUMENT' || this.type() === 'CHECKLIST') this.openDocument('ai');
    else void this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'ai']);
  }

  download(format: ExportFormat): void {
    const a = this.artifact();
    if (!a) return;
    this.exportImport.export(this.documentWorkspaceId(), a.id, format, a.name).subscribe({
      next: () => this.toast.success(this.i18n.translate('documentView.exportedAs', { format })),
      error: () => this.toast.error(this.i18n.translate('documentView.exportError')),
    });
  }

  downloadFile(): void {
    this.files.downloadUrl(this.documentWorkspaceId(), this.artifactId()).subscribe({
      next: (url) => window.open(url, '_blank', 'noopener'),
      error: () => this.toast.error(this.i18n.translate('documentView.exportError')),
    });
  }

  setColor(color: string): void {
    this.form.controls.color.setValue(this.form.controls.color.value === color ? '' : color);
  }

  /** Back to where the item lives: its document, or the folder it is in for a folder. */
  backToItem(): void {
    if (this.isFolder()) {
      this.back();
      return;
    }
    this.openDocument();
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
  // ---- tags ----
  readonly allTags = signal<Tag[]>([]);
  readonly itemTags = signal<Tag[]>([]);
  readonly tagText = signal('');
  readonly tagMenu = signal(false);

  /** Workspace tags the item does not carry yet, narrowed by what was typed. */
  readonly tagSuggestions = computed(() => {
    const text = this.tagText().trim().toLowerCase();
    const have = new Set(this.itemTags().map((t) => t.id));
    return this.allTags().filter((t) => !have.has(t.id) && (!text || t.name.toLowerCase().includes(text)));
  });
  /** What was typed names no tag yet, so it can be made. */
  readonly canCreateTag = computed(() => {
    const text = this.tagText().trim().toLowerCase();
    return !!text && !this.allTags().some((t) => t.name.toLowerCase() === text);
  });

  private loadTags(): void {
    const ws = this.documentWorkspaceId();
    this.tagService.list(ws).subscribe({ next: (list) => this.allTags.set(list), error: () => this.allTags.set([]) });
    this.tagService.tagsOf(ws, this.artifactId()).subscribe({ next: (list) => this.itemTags.set(list), error: () => this.itemTags.set([]) });
  }

  addTag(tag: Tag): void {
    this.tagService.assign(this.documentWorkspaceId(), this.artifactId(), tag.id).subscribe({
      next: () => {
        this.itemTags.update((list) => [...list, tag]);
        this.tagText.set('');
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  /** Makes a tag from what was typed and puts it on the item. */
  createTag(): void {
    const name = this.tagText().trim();
    if (!name) return;
    const color = this.swatches[this.allTags().length % this.swatches.length];
    this.tagService.create(this.documentWorkspaceId(), name, color).subscribe({
      next: (tag) => {
        this.allTags.update((list) => [...list, tag].sort((a, b) => a.name.localeCompare(b.name)));
        this.addTag(tag);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  onTagEnter(): void {
    const first = this.tagSuggestions().find((t) => t.name.toLowerCase() === this.tagText().trim().toLowerCase()) ?? (this.canCreateTag() ? null : this.tagSuggestions()[0]);
    if (first) this.addTag(first);
    else this.createTag();
  }

  removeTag(tag: Tag): void {
    this.tagService.unassign(this.documentWorkspaceId(), this.artifactId(), tag.id).subscribe({
      next: () => this.itemTags.update((list) => list.filter((t) => t.id !== tag.id)),
      error: (err: AppError) => this.toast.error(err.message),
    });
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
