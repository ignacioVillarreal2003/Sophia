import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {FormControl, ReactiveFormsModule} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {Observable, Subject, forkJoin, of, switchMap} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {AuditLogEntry, AuditLogQuery, AuditResult, User} from '@core/models/authentication';
import {NotificationAuditEntry, NotificationAuditQuery} from '@core/models/notifications';
import {DocumentWorkspace, WorkspaceAuditEntry, WorkspaceAuditQuery} from '@core/models/document-workspace';
import {PageResponse, emptyPage} from '@core/models/api.model';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {NotificationAdminService} from '../notifications/data/notification-admin.service';
import {DocumentWorkspaceService} from '../../document-workspace/data/document-workspace.service';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {IconName} from '@shared/ui/icon/icons';
import {AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {BadgeComponent, BadgeTone} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {SelectOption} from '@shared/ui/form-field/select-field.component';
import {FilterToggleComponent} from '@shared/ui/filter-toggle/filter-toggle.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {
  AuditFiltersDialogComponent,
  AuditFiltersDialogData,
  AuditFiltersValues,
} from './audit-filters-dialog.component';

export type AuditSource = 'auth' | 'notifications' | 'workspaces';

type AuditEntry = AuditLogEntry | NotificationAuditEntry | WorkspaceAuditEntry;

interface AuditVisual {
  readonly icon: IconName;
  readonly tone: BadgeTone;
}

interface AuditField {
  readonly label: string;
  readonly value: string;
  readonly mono: boolean;
}

interface AuditStatusView {
  readonly tone: BadgeTone;
  readonly label: string;
}

interface AuditRow {
  readonly id: string;
  readonly icon: IconName;
  readonly tone: BadgeTone;
  readonly label: string;
  readonly entity: string | null;
  readonly actor: string;
  readonly actorId: string | null;
  readonly ipAddress: string | null;
  readonly resourceId: string | null;
  readonly requestId: string | null;
  readonly createdAt: string;
  readonly timestamp: string;
  readonly status: AuditStatusView | null;
  readonly fields: readonly AuditField[];
  readonly payload: readonly AuditField[];
}

const PAGE_SIZE = 20;

const AUTH_ACTION_VISUALS: Record<string, AuditVisual> = {
  REGISTER: {icon: 'user', tone: 'success'},
  LOGIN: {icon: 'lock', tone: 'accent'},
  LOGIN_GOOGLE: {icon: 'globe', tone: 'accent'},
  LOGIN_FAILED: {icon: 'alert-circle', tone: 'danger'},
  ACCOUNT_LOCKED: {icon: 'lock', tone: 'danger'},
  TOKEN_REUSE_DETECTED: {icon: 'alert-circle', tone: 'danger'},
  LOGOUT: {icon: 'logout', tone: 'neutral'},
  LOGOUT_ALL: {icon: 'logout', tone: 'neutral'},
  EMAIL_VERIFIED: {icon: 'check-circle', tone: 'success'},
  VERIFICATION_RESENT: {icon: 'inbox', tone: 'accent'},
  PASSWORD_RESET_REQUESTED: {icon: 'lock', tone: 'warning'},
  PASSWORD_RESET: {icon: 'lock', tone: 'warning'},
  PASSWORD_CHANGED: {icon: 'lock', tone: 'accent'},
  EMAIL_CHANGE_REQUESTED: {icon: 'inbox', tone: 'warning'},
  EMAIL_CHANGED: {icon: 'inbox', tone: 'accent'},
  ACCOUNT_DELETED: {icon: 'trash', tone: 'danger'},
  GOOGLE_LINKED: {icon: 'link', tone: 'accent'},
  GOOGLE_UNLINKED: {icon: 'link', tone: 'warning'},
  USER_CREATED: {icon: 'plus-circle', tone: 'success'},
  USER_UPDATED: {icon: 'edit', tone: 'accent'},
  USER_DELETED: {icon: 'trash', tone: 'danger'},
  USER_ROLES_UPDATED: {icon: 'shield', tone: 'accent'},
  USER_PERMISSIONS_UPDATED: {icon: 'shield', tone: 'accent'},
  USER_STATUS_UPDATED: {icon: 'user', tone: 'warning'},
  USER_PHOTO_UPDATED: {icon: 'image', tone: 'accent'},
  USER_PASSWORD_RESET_BY_ADMIN: {icon: 'lock', tone: 'warning'},
  EMAIL_UNVERIFIED_BY_ADMIN: {icon: 'alert-circle', tone: 'warning'},
  ROLE_CREATED: {icon: 'shield', tone: 'success'},
  ROLE_UPDATED: {icon: 'shield', tone: 'accent'},
  ROLE_DELETED: {icon: 'shield', tone: 'danger'},
  PERMISSION_CREATED: {icon: 'lock', tone: 'success'},
  PERMISSION_UPDATED: {icon: 'lock', tone: 'accent'},
  PERMISSION_DELETED: {icon: 'lock', tone: 'danger'},
  SESSION_REVOKED: {icon: 'logout', tone: 'warning'},
  SESSIONS_REVOKED_ALL: {icon: 'logout', tone: 'danger'},
  SERVICE_CLIENT_CREATED: {icon: 'code', tone: 'success'},
  SERVICE_CLIENT_UPDATED: {icon: 'code', tone: 'accent'},
  SERVICE_CLIENT_DELETED: {icon: 'code', tone: 'danger'},
  SERVICE_CLIENT_SECRET_ROTATED: {icon: 'lock', tone: 'warning'},
  SERVICE_TOKEN_ISSUED: {icon: 'code', tone: 'accent'},
};

const NOTIFICATION_ACTION_VISUALS: Record<string, AuditVisual> = {
  READ_OTHER_INBOX: {icon: 'eye', tone: 'warning'},
  READ_OTHER_PREFERENCES: {icon: 'eye', tone: 'warning'},
  UPDATE_OTHER_PREFERENCES: {icon: 'edit', tone: 'warning'},
  CREATE_CATEGORY: {icon: 'plus-circle', tone: 'success'},
  UPDATE_CATEGORY: {icon: 'edit', tone: 'accent'},
  DELETE_CATEGORY: {icon: 'trash', tone: 'danger'},
  REPLAY_DEAD_LETTERS: {icon: 'history', tone: 'warning'},
};

const WORKSPACE_ACTION_VISUALS: Record<string, AuditVisual> = {
  'workspace.created': {icon: 'plus-circle', tone: 'success'},
  'workspace.updated': {icon: 'edit', tone: 'accent'},
  'workspace.settings-updated': {icon: 'settings', tone: 'accent'},
  'workspace.suspended': {icon: 'lock', tone: 'warning'},
  'workspace.reactivated': {icon: 'check-circle', tone: 'success'},
  'workspace.restored': {icon: 'history', tone: 'success'},
  'workspace.deleted': {icon: 'trash', tone: 'danger'},
  'workspace.ownership-transferred': {icon: 'users', tone: 'warning'},
  'workspace-member.added': {icon: 'users', tone: 'success'},
  'workspace-member.removed': {icon: 'users', tone: 'danger'},
  'workspace-member.role-changed': {icon: 'shield', tone: 'accent'},
  'workspace-member.suspended': {icon: 'lock', tone: 'warning'},
  'workspace-member.reactivated': {icon: 'check-circle', tone: 'success'},
  'artifact.created': {icon: 'file-text', tone: 'success'},
  'artifact.updated': {icon: 'edit', tone: 'accent'},
  'artifact.moved': {icon: 'folder', tone: 'accent'},
  'artifact.duplicated': {icon: 'copy', tone: 'accent'},
  'artifact.trashed': {icon: 'trash', tone: 'warning'},
  'artifact.restored': {icon: 'history', tone: 'success'},
  'artifact.purged': {icon: 'trash', tone: 'danger'},
  'document.content-updated': {icon: 'edit', tone: 'accent'},
  'document.version-named': {icon: 'star', tone: 'accent'},
  'document.version-restored': {icon: 'history', tone: 'warning'},
  'file.uploaded': {icon: 'upload', tone: 'success'},
  'file.version-added': {icon: 'upload', tone: 'accent'},
  'file.version-restored': {icon: 'history', tone: 'warning'},
  'file.preview-generated': {icon: 'image', tone: 'neutral'},
  'comment.created': {icon: 'message-circle', tone: 'success'},
  'comment.updated': {icon: 'message-circle', tone: 'accent'},
  'comment.resolved': {icon: 'check-circle', tone: 'success'},
  'comment.deleted': {icon: 'message-circle', tone: 'danger'},
  'resource-permission.acl-granted': {icon: 'shield', tone: 'success'},
  'resource-permission.acl-revoked': {icon: 'shield', tone: 'danger'},
  'resource-permission.relabelled': {icon: 'shield', tone: 'warning'},
  'template.created': {icon: 'layout-template', tone: 'success'},
  'template.published': {icon: 'layout-template', tone: 'accent'},
  'template.instantiated': {icon: 'copy', tone: 'accent'},
};

const ACTION_VISUALS: Record<string, AuditVisual> = {
  ...AUTH_ACTION_VISUALS,
  ...NOTIFICATION_ACTION_VISUALS,
  ...WORKSPACE_ACTION_VISUALS,
};

const ACTIONS_BY_SOURCE: Record<AuditSource, string[]> = {
  auth: Object.keys(AUTH_ACTION_VISUALS),
  notifications: Object.keys(NOTIFICATION_ACTION_VISUALS),
  workspaces: Object.keys(WORKSPACE_ACTION_VISUALS),
};

const RESOURCE_TYPES_BY_SOURCE: Record<AuditSource, string[]> = {
  auth: ['User', 'Role', 'Permission', 'ServiceClient', 'UserSession'],
  notifications: ['NotificationCategory', 'User', 'KafkaTopic'],
  workspaces: [
    'workspace',
    'artifact',
    'document',
    'file',
    'comment',
    'workspace-member',
    'resource-permission',
    'template',
  ],
};

const ACTION_FALLBACKS: ReadonlyArray<{pattern: RegExp; visual: AuditVisual}> = [
  {pattern: /DELETE|REMOVE|REVOKE|PURGE/, visual: {icon: 'trash', tone: 'danger'}},
  {pattern: /FAIL|DENIED|LOCK|BLOCK|SUSPEND/, visual: {icon: 'alert-circle', tone: 'danger'}},
  {pattern: /TRASH/, visual: {icon: 'trash', tone: 'warning'}},
  {pattern: /RESTORE|REACTIVATE/, visual: {icon: 'history', tone: 'success'}},
  {pattern: /CREATE|ADD|GRANT|ISSUE|UPLOAD/, visual: {icon: 'plus-circle', tone: 'success'}},
  {pattern: /UPDATE|CHANGE|RENAME|ROTATE|MOVE/, visual: {icon: 'edit', tone: 'accent'}},
  {pattern: /ROLE|PERMISSION|SECURITY|CLEARANCE/, visual: {icon: 'shield', tone: 'accent'}},
  {pattern: /COMMENT/, visual: {icon: 'message-circle', tone: 'accent'}},
  {pattern: /READ|VIEW|EXPORT|LIST/, visual: {icon: 'eye', tone: 'neutral'}},
];

@Component({
  selector: 'app-audit',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    CardComponent,
    IconComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PaginationComponent,
    FilterToggleComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AuditComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly notificationApi = inject(NotificationAdminService);
  private readonly workspaceApi = inject(DocumentWorkspaceService);
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialogService = inject(DialogService);
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly source = signal<AuditSource>('auth');
  readonly viewMode = signal<'browse' | 'trace'>('browse');
  private readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly userIdFilter = signal<string | null>(null);
  readonly userEmailFilter = signal<string | null>(null);

  readonly subjectIdFilter = signal<string | null>(null);
  readonly subjectEmailFilter = signal<string | null>(null);

  readonly userSearchTerm = signal('');
  readonly userSearchResults = signal<User[]>([]);
  readonly userSearchLoading = signal(false);
  readonly userSearchOpen = signal(false);
  private readonly userSearch$ = new Subject<string>();

  readonly traceInput = new FormControl<string>('', {nonNullable: true});
  readonly traceRequestId = signal<string | null>(null);
  readonly traceLoading = signal(false);
  readonly traceRows = signal<{source: AuditSource; row: AuditRow}[]>([]);

  private readonly loaded = signal<{source: AuditSource; entries: AuditEntry[]}>({
    source: 'auth',
    entries: [],
  });
  private readonly actorNames = signal<ReadonlyMap<string, string>>(new Map());

  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  readonly actionFilter = new FormControl<string>('', {nonNullable: true});
  readonly resourceTypeFilter = new FormControl<string>('', {nonNullable: true});
  readonly statusFilter = new FormControl<string>('', {nonNullable: true});
  readonly fromFilter = new FormControl<string>('', {nonNullable: true});
  readonly toFilter = new FormControl<string>('', {nonNullable: true});
  readonly workspaceFilter = new FormControl<string>('', {nonNullable: true});

  readonly filtersOpen = signal(false);
  readonly expanded = signal<ReadonlySet<string>>(new Set());

  private readonly auditableWorkspaces = signal<DocumentWorkspace[]>([]);
  readonly workspacesLoaded = signal(false);

  private readonly canReadNotificationAudit = computed(() =>
    this.auth.permissions().has('NOTIFICATION_AUDIT_READ'),
  );
  readonly canReadUsers = computed(() => this.auth.permissions().has('USER_READ'));

  readonly tabs = computed<{value: AuditSource; label: string; icon: IconName}[]>(() => {
    const tabs: {value: AuditSource; label: string; icon: IconName}[] = [
      {value: 'auth', label: this.i18n.translate('admin.audit.tab.auth'), icon: 'shield'},
    ];
    if (this.canReadNotificationAudit()) {
      tabs.push({
        value: 'notifications',
        label: this.i18n.translate('admin.audit.tab.notifications'),
        icon: 'bell',
      });
    }
    tabs.push({
      value: 'workspaces',
      label: this.i18n.translate('admin.audit.tab.workspaces'),
      icon: 'folder',
    });
    return tabs;
  });

  readonly statusOptions = computed<SelectOption[]>(() => [
    {value: '', label: this.i18n.translate('admin.audit.status.all')},
    {value: 'SUCCESS', label: this.i18n.translate('admin.audit.status.success')},
    {value: 'FAILURE', label: this.i18n.translate('admin.audit.status.failure')},
  ]);

  readonly workspaceOptions = computed<SelectOption[]>(() =>
    this.auditableWorkspaces().map((w) => ({value: w.id, label: w.name})),
  );

  readonly actionOptions = computed<SelectOption[]>(() => [
    {value: '', label: this.i18n.translate('admin.audit.filter.allActions')},
    ...ACTIONS_BY_SOURCE[this.source()].map((action) => ({
      value: action,
      label: this.actionLabel(action),
    })),
  ]);

  readonly resourceTypeOptions = computed<SelectOption[]>(() => [
    {value: '', label: this.i18n.translate('admin.audit.filter.allResourceTypes')},
    ...RESOURCE_TYPES_BY_SOURCE[this.source()].map((resourceType) => ({
      value: resourceType,
      label: this.entityLabel(resourceType),
    })),
  ]);

  readonly showStatusFilter = computed(() => true);

  readonly rows = computed<AuditRow[]>(() => {
    const {source, entries} = this.loaded();
    switch (source) {
      case 'auth':
        return (entries as AuditLogEntry[]).map((e) => this.authRow(e));
      case 'notifications':
        return (entries as NotificationAuditEntry[]).map((e) => this.notificationRow(e));
      case 'workspaces':
        return (entries as WorkspaceAuditEntry[]).map((e) => this.workspaceRow(e));
    }
  });

  private readonly refresh$ = new Subject<void>();

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.userIdFilter.set(params.get('userId'));
    this.userEmailFilter.set(params.get('userEmail'));
    const tab = params.get('tab');
    if (tab === 'notifications' || tab === 'workspaces') {
      this.source.set(tab);
    }
    this.actionFilter.setValue(params.get('action') ?? '', {emitEvent: false});
    this.resourceTypeFilter.setValue(params.get('resourceType') ?? '', {emitEvent: false});
    this.statusFilter.setValue(params.get('status') ?? '', {emitEvent: false});
    this.fromFilter.setValue(params.get('from') ?? '', {emitEvent: false});
    this.toFilter.setValue(params.get('to') ?? '', {emitEvent: false});
    const workspaceParam = params.get('workspace');
    if (workspaceParam) this.workspaceFilter.setValue(workspaceParam, {emitEvent: false});
    this.subjectIdFilter.set(params.get('subjectId'));
    this.subjectEmailFilter.set(params.get('subjectEmail'));

    this.loadAuditableWorkspaces();

    this.userSearch$.pipe(switchMap((term) => this.lookupUsers(term))).subscribe((results) => {
      this.userSearchLoading.set(false);
      this.userSearchResults.set(results);
      this.userSearchOpen.set(true);
    });

    this.workspaceFilter.valueChanges.subscribe(() => {
      this.page.set(0);
      this.updateQueryParams();
      this.refresh$.next();
    });

    this.refresh$
      .pipe(
        switchMap(() => {
          this.loading.set(true);
          this.expanded.set(new Set());
          const source = this.source();
          return this.request(source).pipe(
            map((page) => ({source, page})),
            catchError(() => of({source, page: emptyPage<AuditEntry>(PAGE_SIZE)})),
          );
        }),
      )
      .subscribe(({source, page}) => {
        this.loaded.set({source, entries: page.content});
        this.totalPages.set(page.totalPages);
        this.first.set(page.first);
        this.last.set(page.last);
        this.loading.set(false);
        this.resolveActors(source, page.content);
      });

    this.refresh$.next();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.userSearchOpen()) return;
    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.userSearchOpen.set(false);
    }
  }

  onUserSearchInput(value: string): void {
    this.userSearchTerm.set(value);
    if (!value.trim()) {
      this.userSearchResults.set([]);
      this.userSearchOpen.set(false);
    }
  }

  clearUserSearchInput(): void {
    this.userSearchTerm.set('');
    this.userSearchResults.set([]);
    this.userSearchOpen.set(false);
    this.searchInput()?.nativeElement.focus();
  }

  submitUserSearch(term: string): void {
    if (!term.trim()) {
      this.userSearchResults.set([]);
      this.userSearchOpen.set(false);
      return;
    }
    this.userSearchLoading.set(true);
    this.userSearch$.next(term);
  }

  selectSearchedUser(user: User): void {
    this.userIdFilter.set(user.id);
    this.userEmailFilter.set(user.email);
    this.userSearchTerm.set('');
    this.userSearchResults.set([]);
    this.userSearchOpen.set(false);
    this.page.set(0);
    this.updateQueryParams();
    this.refresh$.next();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.refresh$.next();
  }

  selectSource(source: AuditSource): void {
    if (this.viewMode() === 'browse' && this.source() === source) return;
    this.viewMode.set('browse');
    this.source.set(source);
    this.page.set(0);
    this.userIdFilter.set(null);
    this.userEmailFilter.set(null);
    this.subjectIdFilter.set(null);
    this.subjectEmailFilter.set(null);
    this.actionFilter.setValue('', {emitEvent: false});
    this.resourceTypeFilter.setValue('', {emitEvent: false});
    this.statusFilter.setValue('', {emitEvent: false});
    this.fromFilter.setValue('', {emitEvent: false});
    this.toFilter.setValue('', {emitEvent: false});
    if (source === 'workspaces') {
      const first = this.auditableWorkspaces()[0];
      this.workspaceFilter.setValue(first ? first.id : '', {emitEvent: false});
    } else {
      this.workspaceFilter.setValue('', {emitEvent: false});
    }
    this.userSearchTerm.set('');
    this.userSearchResults.set([]);
    this.userSearchOpen.set(false);
    this.updateQueryParams();
    this.refresh$.next();
  }

  enterTraceMode(): void {
    this.viewMode.set('trace');
  }

  openFilters(): void {
    this.filtersOpen.set(true);
    const ref = this.dialogService.open<
      AuditFiltersDialogComponent,
      AuditFiltersDialogData,
      AuditFiltersValues | undefined
    >(AuditFiltersDialogComponent, {
      data: {
        source: this.source(),
        showStatusFilter: this.showStatusFilter(),
        showWorkspaceFilter: this.source() === 'workspaces',
        showSubjectFilter: this.source() === 'notifications',
        actionOptions: this.actionOptions(),
        resourceTypeOptions: this.resourceTypeOptions(),
        statusOptions: this.statusOptions(),
        workspaceOptions: this.workspaceOptions(),
        values: {
          action: this.actionFilter.value,
          resourceType: this.resourceTypeFilter.value,
          status: this.statusFilter.value,
          from: this.fromFilter.value,
          to: this.toFilter.value,
          workspace: this.workspaceFilter.value,
          subjectId: this.subjectIdFilter() ?? '',
          subjectLabel: this.subjectEmailFilter() ?? this.subjectIdFilter() ?? '',
        },
      },
    });
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (result) this.applyFiltersFromDialog(result);
    });
  }

  private applyFiltersFromDialog(result: AuditFiltersValues): void {
    this.actionFilter.setValue(result.action, {emitEvent: false});
    this.resourceTypeFilter.setValue(result.resourceType, {emitEvent: false});
    this.statusFilter.setValue(result.status, {emitEvent: false});
    this.fromFilter.setValue(result.from, {emitEvent: false});
    this.toFilter.setValue(result.to, {emitEvent: false});
    if (this.source() === 'workspaces') {
      this.workspaceFilter.setValue(result.workspace, {emitEvent: false});
    }
    if (this.source() === 'notifications') {
      this.subjectIdFilter.set(result.subjectId || null);
      this.subjectEmailFilter.set(result.subjectLabel || null);
    }
    this.page.set(0);
    this.updateQueryParams();
    this.refresh$.next();
  }

  isOpen(id: string): boolean {
    return this.expanded().has(id);
  }

  toggleRow(id: string): void {
    this.expanded.update((current) => {
      const next = new Set(current);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  }

  filterByActor(row: AuditRow): void {
    if (!row.actorId) return;
    this.userIdFilter.set(row.actorId);
    this.userEmailFilter.set(this.actorNames().get(row.actorId) ?? null);
    this.page.set(0);
    this.updateQueryParams();
    this.refresh$.next();
  }

  filterByRequestId(row: AuditRow): void {
    if (!row.requestId) return;
    this.traceRequest(row.requestId);
  }

  traceRequest(rawId: string): void {
    const trimmed = rawId.trim();
    if (!trimmed) return;
    this.viewMode.set('trace');
    this.traceRequestId.set(trimmed);
    this.traceInput.setValue(trimmed, {emitEvent: false});
    this.traceLoading.set(true);
    this.traceRows.set([]);

    const calls: Observable<{source: AuditSource; row: AuditRow}[]>[] = [
      this.api.listAuditLogs({requestId: trimmed, page: 0, size: 50, sort: 'createdAt,desc'}).pipe(
        map((page) => page.content.map((e) => ({source: 'auth' as AuditSource, row: this.authRow(e)}))),
        catchError(() => of([])),
      ),
    ];
    if (this.canReadNotificationAudit()) {
      calls.push(
        this.notificationApi.auditLog(0, 50, {requestId: trimmed}).pipe(
          map((page) =>
            page.content.map((e) => ({source: 'notifications' as AuditSource, row: this.notificationRow(e)})),
          ),
          catchError(() => of([])),
        ),
      );
    }
    for (const workspace of this.auditableWorkspaces()) {
      calls.push(
        this.workspaceApi.audit(workspace.id, {requestId: trimmed, page: 0, size: 50, sort: 'createdAt,desc'}).pipe(
          map((page) =>
            page.content.map((e) => ({
              source: 'workspaces' as AuditSource,
              row: this.workspaceRowFor(e, workspace, workspace.id),
            })),
          ),
          catchError(() => of([])),
        ),
      );
    }

    forkJoin(calls).subscribe((results) => {
      const merged = results.flat().sort((a, b) => (a.row.createdAt < b.row.createdAt ? 1 : -1));
      this.traceRows.set(merged);
      this.traceLoading.set(false);
    });
  }

  clearTrace(): void {
    this.traceRequestId.set(null);
    this.traceRows.set([]);
    this.traceInput.setValue('', {emitEvent: false});
  }

  activeFilters(): {
    key: 'user' | 'subject' | 'action' | 'resourceType' | 'status' | 'from' | 'to';
    label: string;
  }[] {
    const chips: {
      key: 'user' | 'subject' | 'action' | 'resourceType' | 'status' | 'from' | 'to';
      label: string;
    }[] = [];
    const userId = this.userIdFilter();
    if (userId) {
      chips.push({
        key: 'user',
        label: this.i18n.translate('admin.audit.filteredByUser', {
          user: this.userEmailFilter() ?? userId,
        }),
      });
    }
    const subjectId = this.subjectIdFilter();
    if (subjectId) {
      chips.push({
        key: 'subject',
        label: this.i18n.translate('admin.audit.filteredBySubject', {
          user: this.subjectEmailFilter() ?? subjectId,
        }),
      });
    }
    if (this.actionFilter.value) {
      chips.push({
        key: 'action',
        label: this.i18n.translate('admin.audit.filteredByAction', {
          action: this.actionLabel(this.actionFilter.value),
        }),
      });
    }
    if (this.resourceTypeFilter.value) {
      chips.push({
        key: 'resourceType',
        label: this.i18n.translate('admin.audit.filteredByResourceType', {
          resourceType: this.entityLabel(this.resourceTypeFilter.value),
        }),
      });
    }
    if (this.showStatusFilter() && this.statusFilter.value) {
      chips.push({
        key: 'status',
        label: this.i18n.translate('admin.audit.filteredByStatus', {
          status: this.i18n.translate(
            this.statusFilter.value === 'SUCCESS'
              ? 'admin.audit.status.success'
              : 'admin.audit.status.failure',
          ),
        }),
      });
    }
    if (this.fromFilter.value) {
      chips.push({
        key: 'from',
        label: this.i18n.translate('admin.audit.filteredByFrom', {date: this.fromFilter.value}),
      });
    }
    if (this.toFilter.value) {
      chips.push({
        key: 'to',
        label: this.i18n.translate('admin.audit.filteredByTo', {date: this.toFilter.value}),
      });
    }
    return chips;
  }

  removeFilter(
    key: 'user' | 'subject' | 'action' | 'resourceType' | 'status' | 'from' | 'to',
  ): void {
    switch (key) {
      case 'user':
        this.userIdFilter.set(null);
        this.userEmailFilter.set(null);
        break;
      case 'subject':
        this.subjectIdFilter.set(null);
        this.subjectEmailFilter.set(null);
        break;
      case 'action':
        this.actionFilter.setValue('', {emitEvent: false});
        break;
      case 'resourceType':
        this.resourceTypeFilter.setValue('', {emitEvent: false});
        break;
      case 'status':
        this.statusFilter.setValue('', {emitEvent: false});
        break;
      case 'from':
        this.fromFilter.setValue('', {emitEvent: false});
        break;
      case 'to':
        this.toFilter.setValue('', {emitEvent: false});
        break;
    }
    this.page.set(0);
    this.updateQueryParams();
    this.refresh$.next();
  }

  clearAllFilters(): void {
    this.userIdFilter.set(null);
    this.userEmailFilter.set(null);
    this.subjectIdFilter.set(null);
    this.subjectEmailFilter.set(null);
    this.actionFilter.setValue('', {emitEvent: false});
    this.resourceTypeFilter.setValue('', {emitEvent: false});
    this.statusFilter.setValue('', {emitEvent: false});
    this.fromFilter.setValue('', {emitEvent: false});
    this.toFilter.setValue('', {emitEvent: false});
    this.page.set(0);
    this.updateQueryParams();
    this.refresh$.next();
  }

  private updateQueryParams(): void {
    const source = this.source();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        tab: source === 'auth' ? undefined : source,
        userId: this.userIdFilter() ?? undefined,
        userEmail: this.userEmailFilter() ?? undefined,
        action: this.actionFilter.value || undefined,
        resourceType: this.resourceTypeFilter.value || undefined,
        status: this.showStatusFilter() ? this.statusFilter.value || undefined : undefined,
        from: this.fromFilter.value || undefined,
        to: this.toFilter.value || undefined,
        workspace: source === 'workspaces' ? this.workspaceFilter.value || undefined : undefined,
        subjectId: source === 'notifications' ? this.subjectIdFilter() ?? undefined : undefined,
        subjectEmail: source === 'notifications' ? this.subjectEmailFilter() ?? undefined : undefined,
      },
    });
  }

  private request(source: AuditSource): Observable<PageResponse<AuditEntry>> {
    switch (source) {
      case 'auth':
        return this.api.listAuditLogs(this.authQuery());
      case 'notifications':
        return this.notificationApi.auditLog(this.page(), PAGE_SIZE, {
          action: this.actionFilter.value || undefined,
          userId: this.userIdFilter() || undefined,
          resourceType: this.resourceTypeFilter.value || undefined,
          resourceId: this.subjectIdFilter() || undefined,
          result: (this.statusFilter.value || undefined) as NotificationAuditQuery['result'],
          from: this.fromIso(),
          to: this.toIso(),
        });
      case 'workspaces': {
        const workspaceId = this.workspaceFilter.value;
        if (!workspaceId) return of(emptyPage<AuditEntry>(PAGE_SIZE));
        return this.workspaceApi.audit(workspaceId, {
          page: this.page(),
          size: PAGE_SIZE,
          sort: 'createdAt,desc',
          action: this.actionFilter.value || undefined,
          userId: this.userIdFilter() || undefined,
          resourceType: this.resourceTypeFilter.value || undefined,
          result: (this.statusFilter.value || undefined) as WorkspaceAuditQuery['result'],
          from: this.fromIso(),
          to: this.toIso(),
        });
      }
    }
  }

  private lookupUsers(term: string): Observable<User[]> {
    const trimmed = term.trim();
    if (!trimmed || !this.canReadUsers()) return of([]);
    return this.api
      .listUsers({search: trimmed, size: 6})
      .pipe(
        map((page) => page.content),
        catchError(() => of([])),
      );
  }

  private loadAuditableWorkspaces(): void {
    this.workspaceApi
      .search({size: 100, sort: 'name,asc'})
      .pipe(catchError(() => of(emptyPage<DocumentWorkspace>())))
      .subscribe((page) => {
        const auditable = page.content.filter(
          (w) => w.myRole === 'ADMIN',
        );
        this.auditableWorkspaces.set(auditable);
        this.workspacesLoaded.set(true);
        if (auditable.length === 0) {
          return;
        }
        if (!this.workspaceFilter.value) {
          this.workspaceFilter.setValue(auditable[0].id, {
            emitEvent: this.source() === 'workspaces',
          });
        }
      });
  }

  private resolveActors(source: AuditSource, entries: AuditEntry[]): void {
    if (source === 'auth' || !this.canReadUsers()) return;
    const known = this.actorNames();
    const missing = [
      ...new Set(
        entries
          .filter((e) => !this.entryUserEmail(source, e))
          .map((e) => this.entryActorId(source, e))
          .filter((id): id is string => !!id && !known.has(id)),
      ),
    ];
    if (missing.length === 0) return;

    forkJoin(
      missing.map((id) =>
        this.api.getUser(id).pipe(
          map((user) => [id, user.email] as const),
          catchError(() => of(null)),
        ),
      ),
    ).subscribe((results) => {
      this.actorNames.update((current) => {
        const next = new Map(current);
        for (const result of results) {
          if (result) next.set(result[0], result[1]);
        }
        return next;
      });
    });
  }

  private entryActorId(source: AuditSource, entry: AuditEntry): string | null {
    if (source === 'notifications') return (entry as NotificationAuditEntry).userId;
    if (source === 'workspaces') return (entry as WorkspaceAuditEntry).userId;
    return (entry as AuditLogEntry).userId;
  }

  private entryUserEmail(source: AuditSource, entry: AuditEntry): string | null {
    if (source === 'notifications') return (entry as NotificationAuditEntry).userEmail;
    if (source === 'workspaces') return (entry as WorkspaceAuditEntry).userEmail;
    return (entry as AuditLogEntry).userEmail;
  }

  private authQuery(): AuditLogQuery {
    return {
      page: this.page(),
      size: PAGE_SIZE,
      sort: 'createdAt,desc',
      userId: this.userIdFilter() || undefined,
      action: this.actionFilter.value || undefined,
      resourceType: this.resourceTypeFilter.value || undefined,
      result: (this.statusFilter.value || undefined) as AuditLogQuery['result'],
      from: this.fromIso(),
      to: this.toIso(),
    };
  }

  private fromIso(): string | undefined {
    return this.fromFilter.value ? new Date(this.fromFilter.value).toISOString() : undefined;
  }

  private toIso(): string | undefined {
    return this.toFilter.value
      ? new Date(`${this.toFilter.value}T23:59:59`).toISOString()
      : undefined;
  }

  private toRow(
    entry: {
      id: string;
      userId: string | null;
      userEmail: string | null;
      action: string;
      resourceType: string | null;
      resourceId: string | null;
      details: Record<string, unknown> | null;
      ipAddress: string | null;
      userAgent: string | null;
      result: string;
      requestId: string | null;
      createdAt: string;
    },
    extraFields: AuditField[] = [],
  ): AuditRow {
    const actor = entry.userEmail || this.actorName(entry.userId);
    const fields: AuditField[] = [
      this.field('timestamp', this.formatTimestamp(entry.createdAt)),
      this.field('action', entry.action, true),
      this.field('user', actor),
    ];
    if (entry.userId) fields.push(this.field('userId', entry.userId, true));
    fields.push(...extraFields);
    if (entry.resourceType) fields.push(this.field('resource', this.entityLabel(entry.resourceType)));
    if (entry.resourceId) fields.push(this.field('resourceId', entry.resourceId, true));
    if (entry.ipAddress) fields.push(this.field('ip', entry.ipAddress, true));
    if (entry.userAgent) fields.push(this.field('userAgent', entry.userAgent));
    if (entry.requestId) fields.push(this.field('requestId', entry.requestId, true));

    return {
      id: entry.id,
      ...this.visualFor(entry.action),
      label: this.actionLabel(entry.action),
      entity: entry.resourceType ? this.entityLabel(entry.resourceType) : null,
      actor,
      actorId: entry.userId,
      ipAddress: entry.ipAddress,
      resourceId: entry.resourceId,
      requestId: entry.requestId,
      createdAt: entry.createdAt,
      timestamp: this.formatTimestamp(entry.createdAt),
      status: this.statusView(entry.result),
      fields,
      payload: this.payloadFields(entry.details),
    };
  }

  private authRow(entry: AuditLogEntry): AuditRow {
    return this.toRow(entry);
  }

  private notificationRow(entry: NotificationAuditEntry): AuditRow {
    return this.toRow(entry);
  }

  private workspaceRow(entry: WorkspaceAuditEntry): AuditRow {
    const workspaceId = this.workspaceFilter.value;
    const workspace = this.auditableWorkspaces().find((w) => w.id === workspaceId);
    return this.workspaceRowFor(entry, workspace, workspaceId);
  }

  private workspaceRowFor(
    entry: WorkspaceAuditEntry,
    workspace: DocumentWorkspace | undefined,
    workspaceId: string,
  ): AuditRow {
    return this.toRow(entry, [
      this.field('workspace', workspace?.name ?? workspaceId ?? '', !workspace),
    ]);
  }

  private visualFor(action: string): AuditVisual {
    const known = ACTION_VISUALS[action];
    if (known) return known;
    const upper = action.toUpperCase();
    const match = ACTION_FALLBACKS.find((rule) => rule.pattern.test(upper));
    return match?.visual ?? {icon: 'history', tone: 'neutral'};
  }

  private statusView(status: AuditResult | string): AuditStatusView {
    const success = status === 'SUCCESS';
    return {
      tone: success ? 'success' : 'danger',
      label: this.i18n.translate(
        success ? 'admin.audit.status.success' : 'admin.audit.status.failure',
      ),
    };
  }

  private actorName(actorId: string | null): string {
    if (!actorId) return this.i18n.translate('admin.audit.system');
    return this.actorNames().get(actorId) ?? actorId;
  }

  private actionLabel(action: string): string {
    return this.translateOr(`admin.audit.action.${action}`, action);
  }

  private entityLabel(entityType: string): string {
    return this.translateOr(`admin.audit.entity.${entityType}`, entityType);
  }

  private translateOr(key: string, raw: string): string {
    const translated = this.i18n.translate(key);
    return translated === key ? this.humanize(raw) : translated;
  }

  private humanize(value: string): string {
    const spaced = value
      .replace(/[_\-.]+/g, ' ')
      .replace(/([a-z\d])([A-Z])/g, '$1 $2')
      .trim()
      .toLowerCase();
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  private field(name: string, value: string, mono = false): AuditField {
    return {label: this.i18n.translate(`admin.audit.field.${name}`), value, mono};
  }

  private payloadFields(details: Record<string, unknown> | null): AuditField[] {
    if (!details) return [];
    return Object.entries(details).map(([key, value]) => ({
      label: this.humanize(key),
      value: this.formatValue(value),
      mono: typeof value === 'object' && value !== null,
    }));
  }

  private formatValue(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value, null, 2);
    return String(value);
  }

  private formatTimestamp(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      dateStyle: 'medium',
      timeStyle: 'medium',
    }).format(date);
  }
}
