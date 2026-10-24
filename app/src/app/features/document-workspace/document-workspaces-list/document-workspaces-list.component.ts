import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import {
  DocumentWorkspace,
  DocumentWorkspaceListQuery,
  DocumentWorkspaceRole,
  DocumentWorkspaceStatus,
  WorkspaceOwnership,
  WorkspaceSortKey,
} from '@core/models/document-workspace';
import { AuthenticationService } from '@core/services/authentication';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { DatePipe } from '@angular/common';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { FilterToggleComponent } from '@shared/ui/filter-toggle/filter-toggle.component';
import { SelectOption } from '@shared/ui/form-field/select-field.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import {
  FiltersDialogComponent,
  FiltersDialogData,
  FiltersDialogResult,
} from '@shared/ui/filters-dialog/filters-dialog.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { WorkspaceLogoComponent } from '../ui/workspace-logo.component';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { DuplicateWorkspaceDialogComponent, DuplicateWorkspaceData } from '../list-dialogs/duplicate-workspace-dialog.component';
import { DeleteWorkspaceDialogComponent, DeleteWorkspaceData } from '../list-dialogs/delete-workspace-dialog.component';
import { TransferOwnershipDialogComponent, TransferOwnershipData } from '../list-dialogs/transfer-ownership-dialog.component';
import { StorageRequestDialogComponent, StorageRequestData } from '../list-dialogs/storage-request-dialog.component';

const ROLES: DocumentWorkspaceRole[] = ['ADMIN', 'EDITOR', 'VIEWER'];
const STATUSES: DocumentWorkspaceStatus[] = ['ACTIVE', 'READ_ONLY', 'SUSPENDED'];
const LIST_PAGE_SIZE = 20;
/** Storage usage from which a workspace is flagged as almost full. */

interface SortOption {
  readonly key: WorkspaceSortKey;
  /** Direction the option starts in; picking the active one again flips it. */
  readonly desc: boolean;
}
const SORTS: SortOption[] = [
  { key: 'ACCESSED', desc: true },
  { key: 'ACTIVITY', desc: true },
  { key: 'NAME', desc: false },
  { key: 'CREATED', desc: true },
];

type OwnershipTab = '' | WorkspaceOwnership;

/** `YYYY-MM-DD` (what a date input holds) to the instant the day starts, in the user's time zone. */
function dayStart(value: string, addDays = 0): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return undefined;
  date.setDate(date.getDate() + addDays);
  return date.toISOString();
}

/**
 * The user's workspaces: a plain list or detailed cards (chosen from the sidebar), with search, ownership tabs,
 * role/date/label filters, sorting, favorites and a menu (⋮ or right-click) for the things you do to a workspace.
 */
@Component({
  selector: 'app-document-workspaces-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    PageHeaderComponent,
    ButtonComponent,
    WorkspaceLogoComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    FilterToggleComponent,
    PaginationComponent,
    DatePipe,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './document-workspaces-list.component.html',
  styleUrl: './document-workspaces-list.component.scss',
})
export class DocumentWorkspacesListComponent implements OnInit, AfterViewInit {
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  private readonly searchInputEl = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly items = signal<DocumentWorkspace[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);



  readonly search = signal('');
  readonly ownership = signal<OwnershipTab>('');
  /** Only the workspaces the user pinned (a filter like the others). */
  readonly favorites = signal(false);
  readonly role = signal<string>('');
  readonly status = signal<string>('');
  readonly createdFrom = signal('');
  readonly createdTo = signal('');
  readonly activeFrom = signal('');
  readonly activeTo = signal('');
  readonly sortKey = signal<WorkspaceSortKey>('ACCESSED');
  readonly desc = signal(true);
  readonly filtersOpen = signal(false);

  readonly sorts = SORTS;
  readonly tabs: { value: OwnershipTab; label: string }[] = [
    { value: '', label: 'workspaces.tab.all' },
    { value: 'MINE', label: 'workspaces.tab.mine' },
    { value: 'SHARED', label: 'workspaces.tab.shared' },
  ];

  readonly filterCount = computed(
    () =>
      (this.ownership() ? 1 : 0) +
      (this.favorites() ? 1 : 0) +
      (this.role() ? 1 : 0) +
      (this.status() ? 1 : 0) +
      (this.createdFrom() || this.createdTo() ? 1 : 0) +
      (this.activeFrom() || this.activeTo() ? 1 : 0),
  );
  readonly hasCriteria = computed(() => !!this.search() || this.filterCount() > 0 || !!this.ownership());

  readonly title = computed(() => this.i18n.translate('nav.documentWorkspaces'));
  readonly currentUserId = computed(() => this.auth.user()?.id ?? null);

  private readonly searchInput$ = new Subject<string>();
  private readonly refresh$ = new Subject<void>();
  private started = false;

  ngOnInit(): void {
    this.restoreFromUrl();

    this.searchInput$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((value) => {
      this.search.set(value);
      this.reload();
    });

    this.refresh$
      .pipe(
        switchMap(() => {
          const query = this.buildQuery();
          this.syncUrl();
          this.loading.set(true);
          return this.workspaces.search(query);
        }),
      )
      .subscribe({
        next: (result) => {
          // The last row of a page can disappear (leave, delete, unpin); step back instead of showing nothing.
          if (result.content.length === 0 && result.totalPages > 0 && this.page() > 0) {
            this.page.set(result.totalPages - 1);
            this.refresh$.next();
            return;
          }
          this.items.set(result.content);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
          this.first.set(result.first);
          this.last.set(result.last);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });

    this.started = true;
    this.refresh$.next();
  }

  ngAfterViewInit(): void {
    // Uncontrolled on purpose: typing fast is not fought by the debounced `search` signal.
    const el = this.searchInputEl()?.nativeElement;
    if (el) el.value = this.search();
  }

  // ---- query / url -------------------------------------------------------------------------------------------

  private restoreFromUrl(): void {
    const p = this.route.snapshot.queryParamMap;
    const page = Number(p.get('page'));
    this.search.set(p.get('q') ?? '');
    this.page.set(Number.isFinite(page) && page > 0 ? page : 0);
    const own = p.get('own');
    this.ownership.set(own === 'MINE' || own === 'SHARED' ? own : '');
    this.favorites.set(p.get('fav') === 'true');
    this.role.set(ROLES.includes(p.get('role') as DocumentWorkspaceRole) ? (p.get('role') as string) : '');
    this.status.set(STATUSES.includes(p.get('status') as DocumentWorkspaceStatus) ? (p.get('status') as string) : '');
    this.createdFrom.set(p.get('cf') ?? '');
    this.createdTo.set(p.get('ct') ?? '');
    this.activeFrom.set(p.get('af') ?? '');
    this.activeTo.set(p.get('at') ?? '');
    const sort = p.get('sort') as WorkspaceSortKey | null;
    const known = SORTS.find((s) => s.key === sort);
    if (known) {
      this.sortKey.set(known.key);
      this.desc.set(p.has('desc') ? p.get('desc') === 'true' : known.desc);
    } else {
      this.sortKey.set('ACCESSED');
      this.desc.set(true);
    }
  }

  private syncUrl(): void {
    const isDefaultSort = this.sortKey() === 'ACCESSED' && this.desc();
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        q: this.search() || null,
        own: this.ownership() || null,
        fav: this.favorites() ? 'true' : null,
        role: this.role() || null,
        status: this.status() || null,
        cf: this.createdFrom() || null,
        ct: this.createdTo() || null,
        af: this.activeFrom() || null,
        at: this.activeTo() || null,
        sort: isDefaultSort ? null : this.sortKey(),
        desc: isDefaultSort ? null : this.desc(),
        page: this.page() ? this.page() : null,
      },
    });
  }

  private pageSize(): number {
    return LIST_PAGE_SIZE;
  }

  private buildQuery(): DocumentWorkspaceListQuery {
    return {
      page: this.page(),
      size: this.pageSize(),
      search: this.search() || undefined,
      ownership: this.ownership() || undefined,
      role: (this.role() || undefined) as DocumentWorkspaceRole | undefined,
      status: (this.status() || undefined) as DocumentWorkspaceStatus | undefined,
      pinned: this.favorites() || undefined,
      createdFrom: dayStart(this.createdFrom()),
      createdTo: dayStart(this.createdTo(), 1),
      activeFrom: dayStart(this.activeFrom()),
      activeTo: dayStart(this.activeTo(), 1),
      sort: this.sortKey(),
      desc: this.desc(),
    };
  }

  private reload(): void {
    this.page.set(0);
    this.refresh$.next();
  }

  onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  /** The order is chosen in a dialog, like the filters: what to order by, and which way. */
  openSort(): void {
    const t = (key: string) => this.i18n.translate(key);
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(FiltersDialogComponent, {
      data: {
        title: t('workspaces.sort.label'),
        fields: [
              { key: 'sort', type: 'select', label: t('workspaces.sort.by'), options: SORTS.map((o) => ({ value: o.key, label: t(`workspaces.sort.${o.key}`) })) },
              {
                key: 'dir',
                type: 'select',
                label: t('workspaces.sort.direction'),
                options: [
                  { value: 'asc', label: t('workspaces.sort.asc') },
                  { value: 'desc', label: t('workspaces.sort.desc') },
                ],
              },
            ],
        applyLabel: t('workspaces.sort.apply'),
        clearLabel: t('workspaces.sort.reset'),
        values: { sort: this.sortKey(), dir: this.desc() ? 'desc' : 'asc' },
      },
    });
    ref.closed.subscribe((result) => {
      if (!result) return;
      const known = SORTS.find((o) => o.key === result['sort']);
      this.sortKey.set(known?.key ?? 'ACCESSED');
      this.desc.set(result['dir'] ? result['dir'] === 'desc' : (known ?? SORTS[0]).desc);
      this.reload();
    });
  }

  clearAll(): void {
    this.search.set('');
    const el = this.searchInputEl()?.nativeElement;
    if (el) el.value = '';
    this.ownership.set('');
    this.favorites.set(false);
    this.role.set('');
    this.status.set('');
    this.createdFrom.set('');
    this.createdTo.set('');
    this.activeFrom.set('');
    this.activeTo.set('');
    this.reload();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.refresh$.next();
  }

  openFilters(): void {
    this.filtersOpen.set(true);
    const t = (key: string) => this.i18n.translate(key);
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(
      FiltersDialogComponent,
      {
        data: {
          title: t('documentWorkspaces.filters'),
          groups: [
            {
              label: t('workspaces.filter.tab.general'),
              fields: [
                { key: 'ownership', type: 'select', label: t('workspaces.filter.ownership'), options: this.ownershipOptions() },
                {
                  key: 'favorites',
                  type: 'select',
                  label: t('workspaces.filter.favorites'),
                  options: [
                    { value: '', label: t('workspaces.filter.favorites.all') },
                    { value: 'true', label: t('workspaces.filter.favorites.only') },
                  ],
                },
                { key: 'role', type: 'select', label: t('documentWorkspaces.filter.role'), options: this.roleOptions() },
                { key: 'status', type: 'select', label: t('documentWorkspaces.filter.status'), options: this.statusOptions() },
              ],
            },
            {
              label: t('workspaces.filter.tab.dates'),
              fields: [],
              dateRanges: [
                {
                  fromKey: 'createdFrom',
                  toKey: 'createdTo',
                  fromLabel: t('workspaces.filter.createdFrom'),
                  toLabel: t('workspaces.filter.createdTo'),
                },
                {
                  fromKey: 'activeFrom',
                  toKey: 'activeTo',
                  fromLabel: t('workspaces.filter.activeFrom'),
                  toLabel: t('workspaces.filter.activeTo'),
                },
              ],
            },
          ],
          values: {
            ownership: this.ownership(),
            favorites: this.favorites() ? 'true' : '',
            role: this.role(),
            status: this.status(),
            createdFrom: this.createdFrom(),
            createdTo: this.createdTo(),
            activeFrom: this.activeFrom(),
            activeTo: this.activeTo(),
          },
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (!result) return;
      this.ownership.set((result['ownership'] ?? '') as OwnershipTab);
      this.favorites.set(result['favorites'] === 'true');
      this.role.set(result['role'] ?? '');
      this.status.set(result['status'] ?? '');
      this.createdFrom.set(result['createdFrom'] ?? '');
      this.createdTo.set(result['createdTo'] ?? '');
      this.activeFrom.set(result['activeFrom'] ?? '');
      this.activeTo.set(result['activeTo'] ?? '');
      this.reload();
    });
  }

  private ownershipOptions(): SelectOption[] {
    return this.tabs.map((tab) => ({ value: tab.value, label: this.i18n.translate(tab.label) }));
  }

  private roleOptions(): SelectOption[] {
    return [
      { value: '', label: this.i18n.translate('documentWorkspaces.filter.allRoles') },
      ...ROLES.map((r) => ({ value: r, label: this.i18n.translate(`documentWorkspace.role.${r}`) })),
    ];
  }

  private statusOptions(): SelectOption[] {
    return [
      { value: '', label: this.i18n.translate('documentWorkspaces.filter.allStatuses') },
      ...STATUSES.map((s) => ({ value: s, label: this.i18n.translate(`documentWorkspaces.status.${s}`) })),
    ];
  }

  // ---- per-item helpers ---------------------------------------------------------------------------------------

  isOwner(ws: DocumentWorkspace): boolean {
    return !!this.currentUserId() && ws.ownerId === this.currentUserId();
  }

  roleLabel(role: DocumentWorkspaceRole): string {
    return this.i18n.translate(`documentWorkspace.role.${role}`);
  }

  // ---- actions -----------------------------------------------------------------------------------------------

  open(ws: DocumentWorkspace): void {
    this.router.navigate(['/document-workspaces', ws.id]);
  }

  openSettings(ws: DocumentWorkspace): void {
    this.router.navigate(['/document-workspaces', ws.id, 'settings']);
  }

  togglePin(ws: DocumentWorkspace): void {
    const pinned = !ws.pinned;
    this.workspaces.setPinned(ws.id, pinned).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate(pinned ? 'workspaces.toast.pinned' : 'workspaces.toast.unpinned', { name: ws.name }));
        this.refresh$.next();
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  duplicate(ws: DocumentWorkspace): void {
    const ref = this.dialog.open<DuplicateWorkspaceDialogComponent, DuplicateWorkspaceData, DocumentWorkspace | undefined>(
      DuplicateWorkspaceDialogComponent,
      { data: { workspace: ws } },
    );
    ref.closed.subscribe((copy) => {
      if (!copy) return;
      this.toast.success(this.i18n.translate('workspaces.toast.duplicated', { name: copy.name }), {
        action: { label: this.i18n.translate('common.open'), run: () => this.open(copy) },
      });
      this.refresh$.next();
    });
  }

  exportBackup(ws: DocumentWorkspace): void {
    this.toast.info(this.i18n.translate('workspaces.toast.exporting', { name: ws.name }));
    this.workspaces.downloadArchive(ws.id, ws.name).subscribe({
      error: (err) => this.toast.error(err.message ?? this.i18n.translate('common.error')),
    });
  }

  transfer(ws: DocumentWorkspace): void {
    const ref = this.dialog.open<TransferOwnershipDialogComponent, TransferOwnershipData, boolean>(
      TransferOwnershipDialogComponent,
      { data: { workspace: ws } },
    );
    ref.closed.subscribe((done) => {
      if (!done) return;
      this.toast.success(this.i18n.translate('workspaces.toast.transferred', { name: ws.name }));
      this.refresh$.next();
    });
  }

  requestStorage(ws: DocumentWorkspace): void {
    const ref = this.dialog.open<StorageRequestDialogComponent, StorageRequestData, boolean>(StorageRequestDialogComponent, {
      data: { workspace: ws },
    });
    ref.closed.subscribe((sent) => {
      if (sent) this.toast.success(this.i18n.translate('workspaces.toast.storageRequested'));
    });
  }

  async leave(ws: DocumentWorkspace): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('workspaces.leave.title'),
      message: this.i18n.translate('workspaces.leave.message', { name: ws.name }),
      confirmLabel: this.i18n.translate('workspaces.leave.confirm'),
      danger: true,
    });
    if (!ok) return;
    this.workspaces.leave(ws.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('workspaces.toast.left', { name: ws.name }));
        this.refresh$.next();
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  delete(ws: DocumentWorkspace): void {
    const ref = this.dialog.open<DeleteWorkspaceDialogComponent, DeleteWorkspaceData, boolean>(DeleteWorkspaceDialogComponent, {
      data: { workspace: ws },
    });
    ref.closed.subscribe((done) => {
      if (!done) return;
      this.toast.success(this.i18n.translate('workspaces.toast.deleted', { name: ws.name }), {
        action: { label: this.i18n.translate('workspaces.toast.viewTrash'), run: () => this.router.navigate(['/document-workspaces/trash']) },
      });
      this.refresh$.next();
    });
  }
}
