import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, map } from 'rxjs';
import { DocumentWorkspaceMember, WorkspaceAuditEntry, WorkspaceAuditQuery, WorkspaceAuditResult } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { FiltersDialogComponent, FiltersDialogData, FiltersDialogResult } from '@shared/ui/filters-dialog/filters-dialog.component';
import { FilterToggleComponent } from '@shared/ui/filter-toggle/filter-toggle.component';
import { SelectOption } from '@shared/ui/form-field/select-field.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';
import { bindUrlState } from '@shared/utils/url-state';

/** Every kind of thing the feed can report, to filter by. */
const ACTIONS: string[] = ['workspace.created', 'workspace.updated', 'workspace.settings-updated', 'workspace.suspended', 'workspace.reactivated', 'workspace.restored', 'workspace.deleted', 'workspace.purged', 'workspace.status-changed', 'workspace.limits-updated', 'workspace.ownership-transferred', 'workspace-member.added', 'workspace-member.removed', 'workspace-member.role-changed', 'artifact.created', 'artifact.updated', 'artifact.moved', 'artifact.duplicated', 'artifact.trashed', 'artifact.restored', 'artifact.purged', 'document.content-updated', 'file.uploaded', 'comment.created', 'comment.deleted', 'resource-permission.acl-granted', 'resource-permission.acl-revoked', 'resource-permission.relabelled'];

interface Row {
  readonly id: string;
  readonly actor: string;
  readonly photoUrl: string | null;
  readonly label: string;
  readonly detail: string;
  readonly createdAt: string;
  readonly failed: boolean;
}

/** Activity feed of one workspace: who did what, newest first, grouped by day. */
@Component({
  selector: 'app-workspace-activity',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WorkspaceDatePipe,
    AvatarComponent,
    ButtonComponent,
    EmptyStateComponent,
    FilterToggleComponent,
    IconComponent,
    PageHeaderComponent,
    PaginationComponent,
    SpinnerComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './workspace-activity.component.html',
  // The search box and the filters button are the ones of the workspace list.
  styleUrls: ['./workspace-activity.component.scss', '../document-workspaces-list/document-workspaces-list.component.scss'],
})
export class WorkspaceActivityComponent implements OnInit {
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);

  /** What the person typed in the search box, before it settles. */
  readonly typed = new Subject<string>();
  readonly q = signal('');
  readonly action = signal('');
  readonly userId = signal('');
  readonly result = signal('');
  readonly from = signal('');
  readonly to = signal('');
  readonly filtersOpen = signal(false);

  readonly filterCount = computed(() => [this.action(), this.userId(), this.result(), this.from(), this.to()].filter((v) => v !== '').length);
  readonly hasCriteria = computed(() => this.filterCount() > 0 || this.q().trim() !== '');

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();
  /** Only what happened to this item (empty: the whole workspace). */
  readonly artifactId = input('');
  /** Inside another page: no page title of its own. */
  readonly embedded = input(false);

  readonly entries = signal<WorkspaceAuditEntry[]>([]);
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly loading = signal(true);
  readonly forbidden = signal(false);
  readonly page = signal(0);

  /** The address and this state are one: filters, order and page are read from it and kept in it. */
  private readonly urlState = bindUrlState([
    { signal: this.q, param: 'q' },
    { signal: this.action, param: 'action' },
    { signal: this.userId, param: 'user' },
    { signal: this.result, param: 'result' },
    { signal: this.from, param: 'from' },
    { signal: this.to, param: 'to' },
    { signal: this.page, param: 'page', page: true },
  ]);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  readonly rows = computed<Row[]>(() => this.entries().map((entry) => this.toRow(entry)));

  constructor() {
    this.typed.pipe(debounceTime(300), map((text) => text.trim()), takeUntilDestroyed()).subscribe((text) => {
      if (text !== this.q()) {
        this.q.set(text);
        this.page.set(0);
        this.load();
      }
    });
  }

  ngOnInit(): void {
    this.workspaces.members(this.documentWorkspaceId()).subscribe({ next: (list) => this.members.set(list) });
    this.load();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.workspaces.audit(this.documentWorkspaceId(), this.query()).subscribe({
      next: (result) => {
        this.entries.set(result.content);
        this.totalPages.set(result.totalPages);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: (err) => {
        this.forbidden.set(err?.status === 403);
        this.loading.set(false);
      },
    });
  }

  private query(): WorkspaceAuditQuery {
    const query: WorkspaceAuditQuery = { page: this.page(), size: 20, sort: 'createdAt,desc' };
    if (this.artifactId()) query.resourceId = this.artifactId();
    if (this.q()) query.q = this.q();
    if (this.action()) query.action = this.action();
    if (this.userId()) query.userId = this.userId();
    if (this.result()) query.result = this.result() as WorkspaceAuditResult;
    // Whole days, the last one included.
    if (this.from()) query.from = `${this.from()}T00:00:00Z`;
    if (this.to()) query.to = `${this.to()}T23:59:59Z`;
    return query;
  }

  clearAll(): void {
    this.q.set('');
    this.action.set('');
    this.userId.set('');
    this.result.set('');
    this.from.set('');
    this.to.set('');
    this.page.set(0);
    this.load();
  }

  openFilters(): void {
    this.filtersOpen.set(true);
    const t = (key: string) => this.i18n.translate(key);
    const actions: SelectOption[] = [
      { value: '', label: t('activity.filter.allActions') },
      ...ACTIONS
        .map((key) => ({ value: key, label: this.actionLabel(key) }))
        .sort((x, y) => x.label.localeCompare(y.label)),
    ];
    const people: SelectOption[] = [
      { value: '', label: t('activity.filter.anyone') },
      ...this.members().map((m) => ({ value: m.userId, label: m.fullName || m.email })),
    ];
    const results: SelectOption[] = [
      { value: '', label: t('activity.filter.anyResult') },
      { value: 'SUCCESS', label: t('activity.filter.success') },
      { value: 'FAILURE', label: t('activity.filter.failure') },
    ];
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(FiltersDialogComponent, {
      data: {
        title: t('documentWorkspaces.filters'),
        groups: [
          {
            label: t('workspaces.filter.tab.general'),
            fields: [
              { key: 'action', type: 'select', label: t('activity.filter.action'), options: actions },
              { key: 'userId', type: 'select', label: t('activity.filter.person'), options: people },
              { key: 'result', type: 'select', label: t('activity.filter.result'), options: results },
            ],
          },
          {
            label: t('workspaces.filter.tab.dates'),
            fields: [],
            dateRanges: [{ fromKey: 'from', toKey: 'to', fromLabel: t('shared.filter.from'), toLabel: t('shared.filter.to') }],
          },
        ],
        values: { action: this.action(), userId: this.userId(), result: this.result(), from: this.from(), to: this.to() },
      },
    });
    ref.closed.subscribe((picked) => {
      this.filtersOpen.set(false);
      if (!picked) return;
      this.action.set(picked['action'] ?? '');
      this.userId.set(picked['userId'] ?? '');
      this.result.set(picked['result'] ?? '');
      this.from.set(picked['from'] ?? '');
      this.to.set(picked['to'] ?? '');
      this.page.set(0);
      this.load();
    });
  }

  private actionLabel(action: string): string {
    const key = `admin.audit.action.${action}`;
    const label = this.i18n.translate(key);
    return label !== key ? label : action;
  }

  private toRow(entry: WorkspaceAuditEntry): Row {
    const member = this.members().find((m) => m.userId === entry.userId);
    const actor = member?.fullName || member?.email || entry.userEmail || this.i18n.translate('activity.system');
    const key = `admin.audit.action.${entry.action}`;
    const translated = this.i18n.translate(key);
    const details = entry.details ?? {};
    const name = typeof details['name'] === 'string' ? (details['name'] as string) : '';
    const type = typeof details['type'] === 'string' ? (details['type'] as string) : '';
    return {
      id: entry.id,
      actor,
      photoUrl: member?.photoUrl ?? null,
      label: translated !== key ? translated : entry.action,
      detail: [name, type ? this.typeLabel(type) : ''].filter(Boolean).join(' · '),
      createdAt: entry.createdAt,
      failed: entry.result === 'FAILURE',
    };
  }

  private typeLabel(type: string): string {
    const key = `newItem.type.${type}`;
    const label = this.i18n.translate(key);
    return label !== key ? label : type.toLowerCase().replace(/_/g, ' ');
  }
}
