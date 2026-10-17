import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { Artifact, ExportFormat, PresenceUser } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { FavoriteService } from '../data/favorite.service';
import { ArtifactService } from '../data/artifact.service';
import { DocumentContentService } from '../data/document-content.service';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ExportImportService } from '../data/export-import.service';
import { FileService } from '../data/file.service';
import { RealtimeService } from '../data/realtime.service';
import { ShareDialogComponent, ShareDialogData } from '../ui/share-dialog.component';
import { FileViewerComponent } from '../ui/file-viewer.component';
import { PresenceBarComponent } from '../ui/presence-bar.component';
import { CommentThreadComponent } from '../comments/comment-thread.component';
import { RevisionHistoryComponent } from '../revisions/revision-history.component';
import { LinkTarget, MarkdownEditorComponent, MarkdownMode } from '../markdown/markdown-editor.component';
import { SpreadsheetComponent } from '../spreadsheet/spreadsheet.component';
import { PresentationComponent } from '../presentation/presentation.component';
import { WhiteboardComponent } from '../whiteboard/whiteboard.component';
import { markdownStats } from '../markdown/markdown-render';

const PRESENCE_COLORS = ['#0066cc', '#5856d6', '#af52de', '#ff9500', '#34c759', '#00c7be'];
const MODE_KEY = 'sophia.markdown.mode';

type SidePanel = 'none' | 'comments' | 'history';
type SaveState = 'saved' | 'dirty' | 'saving' | 'error' | 'conflict';

const TEXT_EXPORTS: ExportFormat[] = ['MARKDOWN', 'HTML', 'PDF', 'DOCX', 'TXT'];
const SHEET_EXPORTS: ExportFormat[] = ['XLSX', 'CSV', 'PDF', 'JSON'];
const DECK_EXPORTS: ExportFormat[] = ['PPTX', 'MARKDOWN', 'PDF', 'HTML', 'JSON'];
const BOARD_EXPORTS: ExportFormat[] = ['JSON', 'MARKDOWN', 'PDF'];

/**
 * Document surface. Markdown notes and checklists open in the Markdown editor (edit / split / preview),
 * spreadsheets in the grid, and uploaded files in a viewer. Text is autosaved with optimistic versioning;
 * a concurrent edit by someone else surfaces as a conflict instead of being overwritten silently.
 */
@Component({
  selector: 'app-document-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    MarkdownEditorComponent,
    SpreadsheetComponent,
    PresentationComponent,
    WhiteboardComponent,
    FileViewerComponent,
    CommentThreadComponent,
    RevisionHistoryComponent,
    PresenceBarComponent,
    ButtonComponent,
    IconComponent,
    SpinnerComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './document-view.component.html',
  styleUrl: './document-view.component.scss',
})
export class DocumentViewComponent implements OnDestroy {
  private readonly artifacts = inject(ArtifactService);
  private readonly content = inject(DocumentContentService);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly favorites = inject(FavoriteService);
  private readonly files = inject(FileService);
  private readonly realtime = inject(RealtimeService);
  private readonly exportImport = inject(ExportImportService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly router = inject(Router);

  readonly documentWorkspaceId = input.required<string>();
  readonly artifactId = input.required<string>();

  private readonly editor = viewChild(MarkdownEditorComponent);
  private readonly sheet = viewChild(SpreadsheetComponent);
  private readonly deck = viewChild(PresentationComponent);
  private readonly board = viewChild(WhiteboardComponent);

  readonly artifact = signal<Artifact | null>(null);
  readonly text = signal('');
  readonly version = signal(0);
  readonly loading = signal(true);
  readonly saveState = signal<SaveState>('saved');
  readonly isFavorite = signal(false);
  readonly panel = signal<SidePanel>('none');
  readonly presence = signal<PresenceUser[]>([]);
  readonly mode = signal<MarkdownMode>(this.storedMode());
  readonly linkTargets = signal<LinkTarget[]>([]);
  readonly title = signal('');

  private joined: { ws: string; doc: string } | null = null;
  /** The document whose text is in `text`/`version`; routed inputs may already point somewhere else. */
  private loaded: { ws: string; id: string } | null = null;
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private saving = false;
  private dirtyWhileSaving = false;

  readonly isMarkdown = computed(() => {
    const t = this.artifact()?.type;
    return t === 'MARKDOWN_DOCUMENT' || t === 'CHECKLIST';
  });
  readonly isChecklist = computed(() => this.artifact()?.type === 'CHECKLIST');
  readonly isSpreadsheet = computed(() => this.artifact()?.type === 'SPREADSHEET');
  readonly isPresentation = computed(() => this.artifact()?.type === 'PRESENTATION');
  readonly isWhiteboard = computed(() => this.artifact()?.type === 'WHITEBOARD');
  /** Documents whose content is JSON edited by its own component (not the Markdown editor). */
  readonly isStructured = computed(() => this.isSpreadsheet() || this.isPresentation() || this.isWhiteboard());
  readonly isText = computed(() => this.isMarkdown() || this.isStructured());
  readonly isFile = computed(() => !!this.artifact()?.mimeType);

  /** Viewers, suspended members and read-only workspaces only read. */
  readonly canEdit = computed(() => {
    const ws = this.workspaces.active();
    return !!ws && ws.status === 'ACTIVE' && ws.myRole !== 'VIEWER';
  });
  readonly exportFormats = computed<ExportFormat[]>(() =>
    this.isSpreadsheet() ? SHEET_EXPORTS : this.isPresentation() ? DECK_EXPORTS : this.isWhiteboard() ? BOARD_EXPORTS : TEXT_EXPORTS,
  );
  readonly progress = computed(() => (this.isChecklist() ? markdownStats(this.text()) : null));
  readonly quickItem = signal('');

  constructor() {
    effect(() => {
      const ws = this.documentWorkspaceId();
      const id = this.artifactId();
      untracked(() => this.load(ws, id));
    });

    this.realtime.awareness$.pipe(takeUntilDestroyed()).subscribe((frame) => {
      if (frame.clientId === this.realtime.clientId) return;
      this.presence.update((list) => {
        if (list.some((u) => u.userId === frame.userId)) return list;
        return [
          ...list,
          {
            userId: frame.userId,
            fullName: frame.email?.split('@')[0] ?? this.i18n.translate('documentView.fallbackCollaborator'),
            color: PRESENCE_COLORS[list.length % PRESENCE_COLORS.length],
            cursorBlockId: frame.cursor,
          },
        ];
      });
    });
  }

  private storedMode(): MarkdownMode {
    try {
      const value = localStorage.getItem(MODE_KEY);
      if (value === 'edit' || value === 'split' || value === 'preview') return value;
    } catch {
      // Storage can be unavailable; the default applies.
    }
    return 'split';
  }

  onModeChange(mode: MarkdownMode): void {
    this.mode.set(mode);
    try {
      localStorage.setItem(MODE_KEY, mode);
    } catch {
      // Not persisted.
    }
  }

  // ------------------------------------------------------------------
  // Loading
  // ------------------------------------------------------------------

  private load(ws: string, id: string): void {
    this.flushPending();
    this.loaded = { ws, id };
    this.loading.set(true);
    this.panel.set('none');
    this.presence.set([]);
    this.saveState.set('saved');
    this.artifacts.get(ws, id).subscribe({
      next: (artifact) => {
        this.artifact.set(artifact);
        this.title.set(artifact.name);
        if (this.isText()) {
          this.loadContent(ws, id);
          this.joinRealtime(ws, id);
          this.loadLinkTargets(ws);
        } else {
          this.loading.set(false);
        }
      },
      error: () => this.loading.set(false),
    });
  }

  private loadContent(ws: string, id: string): void {
    this.content.get(ws, id).subscribe({
      next: (doc) => {
        this.text.set(doc.content);
        this.version.set(doc.version);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadLinkTargets(ws: string): void {
    if (!this.isMarkdown()) return;
    this.artifacts.documents(ws, '', 200).subscribe({
      next: (docs) => this.linkTargets.set(docs.filter((d) => d.id !== this.artifactId()).map((d) => ({ id: d.id, name: d.name }))),
      error: () => this.linkTargets.set([]),
    });
  }

  private joinRealtime(ws: string, doc: string): void {
    if (this.joined && (this.joined.ws !== ws || this.joined.doc !== doc)) {
      this.realtime.leaveDocument(this.joined.ws, this.joined.doc);
    }
    this.realtime.joinDocument(ws, doc);
    this.realtime.sendAwareness(ws, doc, null, { viewing: true });
    this.joined = { ws, doc };
  }

  ngOnDestroy(): void {
    this.flushPending();
    if (this.joined) this.realtime.leaveDocument(this.joined.ws, this.joined.doc);
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.saveState() === 'dirty' || this.saveState() === 'saving' || this.saveState() === 'conflict') {
      event.preventDefault();
    }
  }

  // ------------------------------------------------------------------
  // Editing and autosave
  // ------------------------------------------------------------------

  onText(value: string): void {
    if (!this.canEdit() || value === this.text()) return;
    this.text.set(value);
    this.saveState.update((s) => (s === 'conflict' ? s : 'dirty'));
    this.scheduleSave(1200);
  }

  /** Spreadsheets, decks and whiteboards emit already-debounced JSON. */
  onSheet(json: string): void {
    if (!this.canEdit() || json === this.text()) return;
    this.text.set(json);
    this.saveState.update((s) => (s === 'conflict' ? s : 'dirty'));
    this.scheduleSave(300);
  }

  private scheduleSave(delay: number): void {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.save(), delay);
  }

  /** Saves right away (Ctrl+S, leaving the page). */
  saveNow(): void {
    this.flushEditors();
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.save();
  }

  private flushEditors(): void {
    this.sheet()?.flush();
    this.deck()?.flush();
    this.board()?.flush();
  }

  private flushPending(): void {
    this.flushEditors();
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.save();
    }
  }

  private save(): void {
    this.saveTimer = null;
    if (this.saveState() === 'conflict' || !this.artifact()) return;
    if (this.saving) {
      this.dirtyWhileSaving = true;
      return;
    }
    const target = this.loaded;
    if (!target) return;
    const { ws, id } = target;
    const text = this.text();
    this.saving = true;
    this.saveState.set('saving');
    this.content.save(ws, id, text, this.version()).subscribe({
      next: (doc) => {
        this.saving = false;
        if (id !== this.loaded?.id) return;
        this.version.set(doc.version);
        this.artifact.update((a) => (a ? { ...a, updatedAt: doc.updatedAt, contentVersion: doc.version } : a));
        if (this.dirtyWhileSaving || this.text() !== text) {
          this.dirtyWhileSaving = false;
          this.saveState.set('dirty');
          this.scheduleSave(200);
        } else {
          this.saveState.set('saved');
        }
      },
      error: (err) => {
        this.saving = false;
        this.dirtyWhileSaving = false;
        this.saveState.set(err?.status === 409 || err?.code === 'OPTIMISTIC_LOCK_CONFLICT' ? 'conflict' : 'error');
        if (this.saveState() === 'error') this.toast.error(this.i18n.translate('documentView.saveError'));
      },
    });
  }

  retrySave(): void {
    this.saveState.set('dirty');
    this.save();
  }

  /** Someone else saved first: take their version and discard the local edit. */
  useRemote(): void {
    this.saveState.set('saved');
    this.loadContent(this.documentWorkspaceId(), this.artifactId());
  }

  /** Someone else saved first: keep the local text and write it over theirs. */
  overwriteRemote(): void {
    this.content.get(this.documentWorkspaceId(), this.artifactId()).subscribe({
      next: (doc) => {
        this.version.set(doc.version);
        this.saveState.set('dirty');
        this.save();
      },
    });
  }

  // ------------------------------------------------------------------
  // Markdown extras
  // ------------------------------------------------------------------

  addChecklistItem(): void {
    const item = this.quickItem().trim();
    if (!item || !this.canEdit()) return;
    const current = this.text().replace(/\s+$/, '');
    this.onText(`${current}${current ? '\n' : ''}- [ ] ${item}\n`);
    this.quickItem.set('');
  }

  openWikilink(target: string): void {
    const ws = this.documentWorkspaceId();
    this.artifacts.documents(ws, target, 20).subscribe({
      next: (docs) => {
        const exact = docs.find((d) => d.name.toLowerCase() === target.toLowerCase()) ?? docs[0];
        if (exact) {
          void this.router.navigate(['/document-workspaces', ws, 'a', exact.id]);
          return;
        }
        if (!this.canEdit()) {
          this.toast.error(this.i18n.translate('markdown.noteNotFound', { name: target }));
          return;
        }
        this.artifacts
          .create(ws, { type: 'MARKDOWN_DOCUMENT', name: target, parentId: this.artifact()?.parentId ?? null, content: `# ${target}\n\n` })
          .subscribe({
            next: (created) => {
              this.toast.success(this.i18n.translate('markdown.noteCreated', { name: target }));
              void this.router.navigate(['/document-workspaces', ws, 'a', created.id]);
            },
            error: (err) => this.toast.error(err.message),
          });
      },
    });
  }

  uploadImage(file: File): void {
    const ws = this.documentWorkspaceId();
    this.files.upload(ws, file, this.artifact()?.parentId ?? null).subscribe({
      next: (event) => {
        if (event.type === 'done') {
          this.files.downloadUrl(ws, event.artifact.id).subscribe((url) => this.editor()?.insertText(`![${file.name}](${url})`));
        }
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  rename(): void {
    const name = this.title().trim();
    const artifact = this.artifact();
    if (!artifact || !name || name === artifact.name || !this.canEdit()) {
      this.title.set(artifact?.name ?? '');
      return;
    }
    this.artifacts.update(this.documentWorkspaceId(), this.artifactId(), { name }).subscribe({
      next: (updated) => this.artifact.set(updated),
      error: (err) => {
        this.title.set(artifact.name);
        this.toast.error(err.message);
      },
    });
  }

  // ------------------------------------------------------------------
  // Toolbar actions
  // ------------------------------------------------------------------

  toggleFavorite(): void {
    const id = this.artifactId();
    const action = this.isFavorite()
      ? this.favorites.remove(this.documentWorkspaceId(), id)
      : this.favorites.add(this.documentWorkspaceId(), id);
    action.subscribe({
      next: () => this.isFavorite.update((v) => !v),
      error: () => this.toast.error(this.i18n.translate('common.error')),
    });
  }

  togglePanel(panel: SidePanel): void {
    this.panel.update((cur) => (cur === panel ? 'none' : panel));
  }

  onRevisionRestored(): void {
    this.panel.set('none');
    this.saveState.set('saved');
    this.loadContent(this.documentWorkspaceId(), this.artifactId());
    this.toast.success(this.i18n.translate('documentView.versionRestored'));
  }

  export(format: ExportFormat): void {
    this.flushPending();
    const name = this.artifact()?.name ?? this.i18n.translate('documentView.fallbackName');
    // The export reads the saved version, so wait for the autosave to land first.
    setTimeout(() => {
      this.exportImport.export(this.documentWorkspaceId(), this.artifactId(), format, name).subscribe({
        next: () => this.toast.success(this.i18n.translate('documentView.exportedAs', { format })),
        error: () => this.toast.error(this.i18n.translate('documentView.exportError')),
      });
    }, 500);
  }

  /** Browser print dialog on the rendered note: the best-looking PDF. */
  print(): void {
    this.onModeChange('preview');
    setTimeout(() => window.print(), 250);
  }

  formatLabel(format: ExportFormat): string {
    return format === 'MARKDOWN' ? 'Markdown' : format === 'XLSX' ? 'Excel (.xlsx)' : format === 'DOCX' ? 'Word (.docx)' : format === 'PPTX' ? 'PowerPoint (.pptx)' : format;
  }

  openShare(): void {
    this.dialog.open<ShareDialogComponent, ShareDialogData>(ShareDialogComponent, {
      data: { documentWorkspaceId: this.documentWorkspaceId(), artifactId: this.artifactId() },
    });
  }
}
