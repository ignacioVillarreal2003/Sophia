import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { CdkContextMenuTrigger, CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { Router } from '@angular/router';
import { firstValueFrom, forkJoin } from 'rxjs';
import { Artifact, ArtifactType, BreadcrumbEntry, DocumentWorkspaceMember, KIND_BY_TYPE } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { Crumb, BreadcrumbsComponent } from '@shared/ui/breadcrumbs/breadcrumbs.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { ArtifactService } from '../data/artifact.service';
import { FavoriteService } from '../data/favorite.service';
import { ShareDialogComponent, ShareDialogData } from '../ui/share-dialog.component';
import { MoveDialogComponent, MoveDialogData, MoveDialogResult } from '../ui/move-dialog.component';
import { UploadManagerService } from '../data/upload-manager.service';
import { ExportImportService } from '../data/export-import.service';
import { DropZoneDirective } from '@shared/directives/drop-zone.directive';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { RenameDialogComponent, RenameDialogData } from '../ui/rename-dialog.component';

/** Folder contents, Drive-style: folders and files, right-click to create, drag & drop to upload. */
@Component({
  selector: 'app-explorer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkContextMenuTrigger,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    ButtonComponent,
    SpinnerComponent,
    BreadcrumbsComponent,
    DropZoneDirective,
    IconComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './explorer.component.html',
  styleUrl: './explorer.component.scss',
})
export class ExplorerComponent {
  private readonly artifacts = inject(ArtifactService);
  private readonly favorites = inject(FavoriteService);
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly uploads = inject(UploadManagerService);
  private readonly exportImport = inject(ExportImportService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly importing = signal(false);

  readonly uploadInput = viewChild.required<ElementRef<HTMLInputElement>>('uploadEl');
  readonly importInput = viewChild.required<ElementRef<HTMLInputElement>>('importEl');

  readonly view = signal<'grid' | 'list'>(this.readView());
  readonly selectedId = signal<string | null>(null);
  /** Ids of the items the user starred in this workspace. */
  readonly starred = signal<ReadonlySet<string>>(new Set());
  readonly icon = artifactIcon;

  /** Route params (bound via withComponentInputBinding). */
  readonly documentWorkspaceId = input.required<string>();
  readonly folderId = input<string | undefined>(undefined);

  readonly items = signal<Artifact[]>([]);
  readonly loading = signal(true);
  readonly crumbs = signal<Crumb[]>([]);

  // ---- filters ----
  readonly search = signal('');
  readonly typeFilter = signal<'all' | 'folders' | 'documents' | 'files'>('all');
  readonly sort = signal<'modified' | 'name'>('modified');
  /** Empty string = anyone; otherwise the owner's user id. */
  readonly personFilter = signal('');
  readonly modifiedFilter = signal<'any' | 'today' | 'week' | 'month' | 'year'>('any');
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly filtersActive = computed(
    () =>
      this.search().trim() !== '' ||
      this.typeFilter() !== 'all' ||
      this.personFilter() !== '' ||
      this.modifiedFilter() !== 'any',
  );

  /** What survives the search and type filters, ordered as chosen. */
  readonly visible = computed(() => {
    const text = this.search().trim().toLowerCase();
    const type = this.typeFilter();
    const person = this.personFilter();
    const since = ExplorerComponent.since(this.modifiedFilter());
    const list = this.items().filter((a) => {
      if (text && !a.name.toLowerCase().includes(text)) return false;
      if (person && a.ownerId !== person) return false;
      if (since !== null && new Date(a.updatedAt).getTime() < since) return false;
      return type === 'all' || ExplorerComponent.group(a.type) === type;
    });
    return [...list].sort((a, b) =>
      this.sort() === 'name'
        ? a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
        : b.updatedAt.localeCompare(a.updatedAt),
    );
  });
  readonly folders = computed(() => this.visible().filter((a) => a.type === 'FOLDER'));
  readonly files = computed(() => this.visible().filter((a) => a.type !== 'FOLDER'));

  private static group(type: ArtifactType): 'folders' | 'documents' | 'files' {
    if (type === 'FOLDER') return 'folders';
    return KIND_BY_TYPE[type] === 'FILE' ? 'files' : 'documents';
  }

  /** Start of the chosen time window as a timestamp, or null for "any time". */
  private static since(range: 'any' | 'today' | 'week' | 'month' | 'year'): number | null {
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    switch (range) {
      case 'today':
        return startOfToday;
      case 'week':
        return startOfToday - 6 * 86_400_000;
      case 'month':
        return startOfToday - 29 * 86_400_000;
      case 'year':
        return new Date(now.getFullYear(), 0, 1).getTime();
      default:
        return null;
    }
  }

  clearFilters(): void {
    this.search.set('');
    this.typeFilter.set('all');
    this.personFilter.set('');
    this.modifiedFilter.set('any');
  }

  /** Folder colour chosen on the details screen . */
  colorOf(artifact: Artifact): string | null {
    return artifact.color || null;
  }

  // ---- drag & drop: drop an item onto a folder to move it ----
  readonly dropTargetId = signal<string | null>(null);
  readonly draggingId = signal<string | null>(null);
  /** Folder that just received an item; pulses briefly as confirmation. */
  readonly receivedId = signal<string | null>(null);
  private dragged: Artifact | null = null;

  onDragStart(event: DragEvent, artifact: Artifact): void {
    this.dragged = artifact;
    this.draggingId.set(artifact.id);
    const transfer = event.dataTransfer;
    if (!transfer) return;
    transfer.setData('text/plain', artifact.id);
    transfer.effectAllowed = 'move';
    const ghost = this.buildDragGhost(artifact, event.currentTarget as HTMLElement | null);
    document.body.appendChild(ghost);
    // Anchor the pointer near the icon so the chip feels held rather than trailing behind.
    transfer.setDragImage(ghost, 36, 34);
    // The browser snapshots the element on the next frame; it can go right after.
    setTimeout(() => ghost.remove());
  }

  /**
   * The chip that follows the pointer: the item's icon on a tinted tile plus its name, on a white card.
   * An outer transparent margin keeps the soft shadow inside the snapshot (drag images clip at the box).
   */
  private buildDragGhost(artifact: Artifact, source: HTMLElement | null): HTMLElement {
    const color = this.colorOf(artifact) ?? '#6b6b66';
    const wrap = document.createElement('div');
    wrap.setAttribute('aria-hidden', 'true');
    wrap.style.cssText = 'position:fixed;top:-300px;left:-300px;padding:14px;pointer-events:none;';

    const card = document.createElement('div');
    card.style.cssText = [
      'display:flex', 'align-items:center', 'gap:10px', 'max-width:240px', 'padding:8px 16px 8px 8px',
      'border-radius:14px', 'background:#fff', 'color:#141413',
      'box-shadow:0 10px 24px -6px rgba(20,20,19,.28),0 0 0 1px rgba(20,20,19,.07)',
      'font:600 13px/1.2 system-ui,-apple-system,Segoe UI,sans-serif',
    ].join(';');

    const tile = document.createElement('span');
    tile.style.cssText = `display:grid;place-items:center;flex:none;width:32px;height:32px;border-radius:9px;background:${color}22;color:${color}`;
    const icon = source?.querySelector('svg')?.cloneNode(true) as SVGElement | undefined;
    if (icon) {
      icon.setAttribute('width', '18');
      icon.setAttribute('height', '18');
      tile.appendChild(icon);
    }

    const name = document.createElement('span');
    name.textContent = artifact.name;
    name.style.cssText = 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap';

    card.append(tile, name);
    wrap.appendChild(card);
    return wrap;
  }

  onDragEnd(): void {
    this.dragged = null;
    this.draggingId.set(null);
    this.dropTargetId.set(null);
  }

  onFolderDragOver(event: DragEvent, folder: Artifact): void {
    if (!this.dragged || folder.type !== 'FOLDER' || this.dragged.id === folder.id) return;
    event.preventDefault();
    this.dropTargetId.set(folder.id);
  }

  onFolderDragLeave(folder: Artifact): void {
    if (this.dropTargetId() === folder.id) this.dropTargetId.set(null);
  }

  onFolderDrop(event: DragEvent, folder: Artifact): void {
    const dragged = this.dragged;
    this.onDragEnd();
    if (!dragged || folder.type !== 'FOLDER' || dragged.id === folder.id) return;
    event.preventDefault();
    this.moveArtifact(dragged, folder.id, folder.id);
  }

  /** Dropping on an earlier level of the path moves the item up to that folder (or the root). */
  onCrumbDrop(crumb: Crumb): void {
    const dragged = this.dragged;
    this.onDragEnd();
    if (!dragged) return;
    this.moveArtifact(dragged, crumb.id);
  }

  private moveArtifact(artifact: Artifact, parentId: string | null, pulseFolderId?: string): void {
    this.artifacts.move(this.documentWorkspaceId(), artifact.id, { newParentId: parentId }).subscribe({
      next: () => {
        this.items.update((list) => list.filter((a) => a.id !== artifact.id));
        this.toast.success(this.i18n.translate('explorer.toast.moved'));
        if (pulseFolderId) {
          this.receivedId.set(pulseFolderId);
          setTimeout(() => this.receivedId.set(null), 800);
        }
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  async moveTo(artifact: Artifact): Promise<void> {
    const ref = this.dialog.open<MoveDialogComponent, MoveDialogData, MoveDialogResult>(MoveDialogComponent, {
      data: { documentWorkspaceId: this.documentWorkspaceId(), artifact },
    });
    const result = await firstValueFrom(ref.closed);
    if (!result) return;
    this.moveArtifact(artifact, result.parentId);
  }

  share(artifact: Artifact): void {
    this.dialog.open<ShareDialogComponent, ShareDialogData>(ShareDialogComponent, {
      data: { documentWorkspaceId: this.documentWorkspaceId(), artifactId: artifact.id },
    });
  }

  toggleStar(artifact: Artifact): void {
    const ws = this.documentWorkspaceId();
    const starred = this.starred().has(artifact.id);
    (starred ? this.favorites.remove(ws, artifact.id) : this.favorites.add(ws, artifact.id)).subscribe({
      next: () => {
        this.starred.update((set) => {
          const next = new Set(set);
          starred ? next.delete(artifact.id) : next.add(artifact.id);
          return next;
        });
        this.toast.success(this.i18n.translate(starred ? 'explorer.toast.unstarred' : 'explorer.toast.starred'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  openDetails(artifact: Artifact): void {
    this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'details', artifact.id]);
  }

  constructor() {
    effect(() => {
      const ws = this.documentWorkspaceId();
      const folder = this.folderId() ?? null;
      this.load(ws, folder);
    });
  }

  private load(documentWorkspaceId: string, folderId: string | null): void {
    this.loading.set(true);
    this.documentWorkspaces.members(documentWorkspaceId).subscribe({
      next: (list) => this.members.set(list),
      error: () => this.members.set([]),
    });
    this.favorites.favorites(documentWorkspaceId).subscribe({
      next: (list) => this.starred.set(new Set(list.map((f) => f.artifactId))),
      error: () => this.starred.set(new Set()),
    });
    this.artifacts.list(documentWorkspaceId, folderId).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    if (folderId) {
      // The breadcrumb endpoint lists the ancestors only, so the folder itself is fetched to close the path.
      forkJoin({
        ancestors: this.artifacts.breadcrumb(documentWorkspaceId, folderId),
        folder: this.artifacts.get(documentWorkspaceId, folderId),
      }).subscribe({
        next: ({ ancestors, folder }) => {
          const path = ancestors.at(-1)?.id === folder.id ? ancestors : [...ancestors, { id: folder.id, name: folder.name }];
          this.crumbs.set(this.toCrumbs(path as BreadcrumbEntry[]));
        },
        error: () => this.crumbs.set([]),
      });
    } else {
      this.crumbs.set([{ id: null, label: this.documentWorkspaces.active()?.name ?? this.i18n.translate('documents.home') }]);
    }
  }

  private toCrumbs(entries: BreadcrumbEntry[]): Crumb[] {
    return [
      { id: null, label: this.documentWorkspaces.active()?.name ?? this.i18n.translate('documents.home') },
      ...entries.map((e) => ({ id: e.id, label: e.name })),
    ];
  }

  pickUpload(): void {
    this.uploadInput().nativeElement.click();
  }

  pickImport(): void {
    this.importInput().nativeElement.click();
  }

  private readView(): 'grid' | 'list' {
    try {
      return localStorage.getItem('sophia.explorer.view') === 'list' ? 'list' : 'grid';
    } catch {
      return 'grid';
    }
  }

  /** Drive behaviour: click selects, double-click opens. Touch screens have no double-click, so a tap opens. */
  onItemClick(artifact: Artifact): void {
    if (typeof matchMedia === 'function' && matchMedia('(hover: none)').matches) {
      this.open(artifact);
      return;
    }
    this.selectedId.set(artifact.id);
  }

  open(artifact: Artifact): void {
    if (artifact.type === 'FOLDER') {
      this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'folder', artifact.id]);
    } else {
      this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'a', artifact.id]);
    }
  }

  navigateCrumb(crumb: Crumb): void {
    if (crumb.id === null) {
      this.router.navigate(['/document-workspaces', this.documentWorkspaceId()]);
    } else {
      this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'folder', crumb.id]);
    }
  }

  async rename(artifact: Artifact): Promise<void> {
    const ref = this.dialog.open<RenameDialogComponent, RenameDialogData, string>(RenameDialogComponent, {
      data: { name: artifact.name },
    });
    const name = await firstValueFrom(ref.closed);
    if (!name || name === artifact.name) return;
    this.artifacts.update(this.documentWorkspaceId(), artifact.id, { name }).subscribe({
      next: (updated) => this.items.update((list) => list.map((a) => (a.id === updated.id ? updated : a))),
      error: (err) => this.toast.error(err.message),
    });
  }

  duplicate(artifact: Artifact): void {
    this.artifacts.duplicate(this.documentWorkspaceId(), artifact.id).subscribe({
      next: (copy) => {
        this.items.update((list) => [...list, copy]);
        this.toast.success(this.i18n.translate('explorer.toast.duplicated'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  trash(artifact: Artifact): void {
    this.artifacts.trash(this.documentWorkspaceId(), artifact.id).subscribe({
      next: () => {
        this.items.update((list) => list.filter((a) => a.id !== artifact.id));
        this.toast.success(this.i18n.translate('explorer.toast.trashed'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  /** Opens the "New item" page for the current folder, optionally with a type preselected. */
  create(type?: ArtifactType): void {
    this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'new'], {
      queryParams: { folder: this.folderId() ?? null, type: type ?? null },
    });
  }

  onFilesDropped(files: File[]): void {
    this.uploads.upload(
      this.documentWorkspaceId(),
      files,
      this.folderId() ?? null,
      (artifact) => this.items.update((list) => [...list, artifact]),
    );
  }

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    if (files.length) {
      this.onFilesDropped(files);
    }
    input.value = '';
  }

  onImportInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.importing.set(true);
    this.exportImport.import(this.documentWorkspaceId(), file, this.folderId() ?? null).subscribe({
      next: () => {
        this.importing.set(false);
        this.toast.success(this.i18n.translate('documents.importSuccess'));
        this.reload();
      },
      error: (err) => {
        this.importing.set(false);
        this.toast.error(err.message ?? this.i18n.translate('documents.importError'));
      },
    });
  }

  private reload(): void {
    this.load(this.documentWorkspaceId(), this.folderId() ?? null);
  }
}
