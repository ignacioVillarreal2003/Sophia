import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, catchError, debounceTime, map, of, switchMap, tap } from 'rxjs';
import { DocumentWorkspaceRole, WorkspaceInvitation } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { FilterField, FiltersDialogComponent, FiltersDialogData, FiltersDialogResult } from '@shared/ui/filters-dialog/filters-dialog.component';
import { FilterToggleComponent } from '@shared/ui/filter-toggle/filter-toggle.component';
import { SelectOption } from '@shared/ui/form-field/select-field.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { WorkspaceLogoComponent } from '../ui/workspace-logo.component';
import { InvitationQuery, InvitationSort, WorkspaceInvitationsService } from '../data/workspace-invitations.service';
import { bindUrlState } from '@shared/utils/url-state';

const SORTS: InvitationSort[] = ['AT', 'EXPIRES', 'NAME'];
const ROLES: DocumentWorkspaceRole[] = ['ADMIN', 'EDITOR', 'VIEWER'];

/**
 * The workspaces other people invited the caller to: accept to become a member, or decline. Searched, filtered, sorted
 * and paged like the workspace list, the server doing the work so any number of invitations stays manageable.
 */
@Component({
  selector: 'app-invitations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AvatarComponent,
    ButtonComponent,
    DatePipe,
    EmptyStateComponent,
    FilterToggleComponent,
    IconComponent,
    PageHeaderComponent,
    PaginationComponent,
    RelativeTimePipe,
    SpinnerComponent,
    TranslatePipe,
    WorkspaceLogoComponent,
  ],
  template: `
    <div class="inv app-container">
      <app-page-header [title]="'invitations.title' | t" />

      <div class="wsl__filters">
        <div class="wsl__search">
          <app-icon name="search" [size]="16" class="wsl__search-icon" />
          <input
            class="wsl__search-input"
            type="search"
            [attr.value]="q()"
            [placeholder]="'invitations.search' | t"
            (input)="typed.next($any($event.target).value)"
          />
        </div>

        <button type="button" class="wsl__tool" (click)="openSort()" [attr.aria-label]="'workspaces.sort.label' | t">
          <app-icon name="arrow-right" [size]="14" class="wsl__sort-icon" [class.wsl__sort-icon--desc]="desc()" />
          <span>{{ 'invitations.sort.' + sort() | t }}</span>
        </button>

        <app-filter-toggle
          [open]="filtersOpen()"
          [label]="'documentWorkspaces.filters' | t"
          [count]="filterCount()"
          (toggle)="openFilters()"
        />
      </div>

      @if (failed()) {
        <app-empty-state icon="alert-circle" [title]="'invitations.error' | t" [description]="'shared.errorHint' | t" />
      } @else if (loading()) {
        <div class="inv__loading"><app-spinner /></div>
      } @else if (items().length === 0) {
        @if (hasCriteria()) {
          <app-empty-state icon="search" [title]="'documentWorkspaces.noResults' | t">
            <button type="button" class="inv__clear" (click)="clearAll()">{{ 'common.filters.clear' | t }}</button>
          </app-empty-state>
        } @else {
          <app-empty-state icon="inbox" [title]="'invitations.empty' | t" [description]="'invitations.emptyHint' | t" />
        }
      } @else {
        <div class="iv__table">
          <div class="iv__head" aria-hidden="true">
            <span>{{ 'invitations.col.workspace' | t }}</span>
            <span>{{ 'invitations.col.role' | t }}</span>
            <span>{{ 'invitations.col.by' | t }}</span>
            <span>{{ 'invitations.col.invited' | t }}</span>
            <span>{{ 'invitations.col.expires' | t }}</span>
            <span>{{ 'workspaces.col.actions' | t }}</span>
          </div>
          @for (inv of items(); track inv.id) {
            <div class="iv__row" [style.--tone]="inv.workspaceColor || 'var(--accent)'">
              <span class="iv__name-cell">
                <app-workspace-logo [pattern]="inv.workspaceColor" [seed]="inv.workspaceId" [size]="40" />
                <span class="iv__name iv__name--row">{{ inv.workspaceName }}</span>
              </span>
              <span class="iv__role iv__role--cell">{{ roleLabel(inv.role) }}</span>
              <span class="iv__by">
                @if (inv.invitedByEmail) {
                  <app-avatar [name]="inv.invitedByEmail" [size]="22" />
                  <span class="iv__by-name">{{ inv.invitedByEmail }}</span>
                }
              </span>
              <span class="iv__when" [attr.title]="inv.createdAt | date: 'medium'">{{ inv.createdAt | relativeTime }}</span>
              <span class="iv__expiry" [class.iv__expiry--soon]="expiresSoon(inv)" [attr.title]="inv.expiresAt | date: 'medium'">
                {{ inv.expiresAt | relativeTime }}
              </span>
              <span class="iv__actions">
                <app-button variant="ghost" size="sm" [disabled]="busy() === inv.id" (click)="decline(inv)">
                  {{ 'invitations.decline' | t }}
                </app-button>
                <app-button variant="dark" size="sm" [loading]="busy() === inv.id" (click)="accept(inv)">
                  {{ 'invitations.accept' | t }}
                </app-button>
              </span>
            </div>
          }
        </div>
      }

      @if (paging(); as p) {
        <app-pagination [page]="p.page" [totalPages]="p.totalPages" [first]="p.first" [last]="p.last" (pageChange)="page.set($event)" />
      }
    </div>
  `,
  styleUrl: '../document-workspaces-list/document-workspaces-list.component.scss',
  styles: `
    .inv { padding-block: var(--space-xl); container-type: inline-size; }
    :host ::ng-deep app-page-header .ph { margin-bottom: var(--space-md); }
    .inv__loading { display: grid; place-items: center; padding: var(--space-section); }
    .inv__clear { padding: 8px 14px; font: inherit; font-size: var(--type-caption-size); font-weight: 600; color: var(--text); background: transparent; border: 0; border-radius: var(--radius-md); box-shadow: inset 0 0 0 1px var(--border); cursor: pointer; }

    /* Decline and Accept are one pair: same height, padding, type and square corners, as in the filters dialog. */
    .iv__actions app-button ::ng-deep .btn, .iv__card-foot app-button ::ng-deep .btn {
      height: 34px; padding-inline: 15px; font-size: var(--type-caption-size); border-radius: var(--radius-sm);
    }

    /* ---- shared pieces ------------------------------------------------------------------------------------- */
    .iv__name { margin: 0; font-size: var(--type-body-strong-size); font-weight: 600; color: var(--text); overflow-wrap: anywhere; }
    .iv__role { font-size: var(--type-fine-size); font-weight: 600; letter-spacing: 0.02em; color: var(--text-muted); }

    /* ---- list: a table like Shared's, headed by its columns ------------------------------------------------ */
    .iv__table {
      --cols: minmax(0, 2fr) 80px minmax(0, 1.6fr) 120px 130px 188px;
      background: var(--surface); border-radius: var(--radius-lg); border: 1px solid var(--border); overflow: hidden;
    }
    .iv__head, .iv__row { display: grid; grid-template-columns: var(--cols); align-items: center; gap: var(--space-md); padding-inline: 18px; }
    .iv__head {
      height: 40px; font-size: 11px; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: var(--text-subtle);
      background: var(--surface-sunken); border-bottom: 1px solid var(--divider);
    }
    .iv__row { min-height: 64px; border-bottom: 1px solid var(--divider); &:last-child { border-bottom: 0; } }
    .iv__name-cell { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .iv__name--row { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .iv__role--cell { font-size: var(--type-caption-size); font-weight: 500; letter-spacing: 0; }
    .iv__by { display: flex; align-items: center; gap: 8px; min-width: 0; font-size: var(--type-caption-size); color: var(--text-muted); }
    .iv__by-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .iv__when, .iv__expiry { font-size: var(--type-caption-size); color: var(--text-subtle); white-space: nowrap; }
    .iv__expiry--soon { color: #b54708; font-weight: 600; }
    .iv__actions { display: inline-flex; align-items: center; justify-content: flex-start; gap: 6px; }
    /* The table adapts to the room it has (the page width minus the sidebar), not to the screen. */
    @container (max-width: 940px) {
      .iv__table { --cols: minmax(0, 1.6fr) 74px minmax(0, 1.4fr) 110px 176px; }
      .iv__head span:nth-child(4), .iv__when { display: none; }
    }
    @container (max-width: 680px) {
      .iv__table { --cols: 40px minmax(0, 1fr); }
      .iv__head { display: none; }
      .iv__row { grid-template-columns: 40px minmax(0, 1fr); row-gap: 10px; padding-block: 14px; }
      .iv__name-cell { display: contents; }
      .iv__role--cell, .iv__by, .iv__expiry { display: none; }
      .iv__actions { grid-column: 2; justify-content: flex-start; }
    }

  `,
})
export class InvitationsComponent {
  private readonly invitations = inject(WorkspaceInvitationsService);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);

  readonly sorts = SORTS;
  readonly typed = new Subject<string>();
  private readonly queries = new Subject<InvitationQuery>();

  readonly q = signal('');
  readonly role = signal<DocumentWorkspaceRole | ''>('');
  readonly invitedBy = signal('');
  readonly from = signal('');
  readonly to = signal('');
  readonly sort = signal<InvitationSort>('AT');
  readonly desc = signal(true);
  readonly page = signal(0);

  /** The address and this state are one: filters, order and page are read from it and kept in it. */
  private readonly urlState = bindUrlState([
    { signal: this.q, param: 'q' },
    { signal: this.role, param: 'role' },
    { signal: this.invitedBy, param: 'by' },
    { signal: this.from, param: 'from' },
    { signal: this.to, param: 'to' },
    { signal: this.sort, param: 'sort' },
    { signal: this.desc, param: 'desc' },
    { signal: this.page, param: 'page', page: true },
  ]);
  readonly filtersOpen = signal(false);

  readonly items = signal<WorkspaceInvitation[]>([]);
  readonly paging = signal<{ page: number; totalPages: number; first: boolean; last: boolean } | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly busy = signal<string | null>(null);

  /** Addresses picked in the filter, so it shows them again when reopened. */
  private readonly inviterLabels: Record<string, string> = {};

  readonly filterCount = computed(() => [this.role(), this.invitedBy(), this.from(), this.to()].filter((v) => v !== '').length);
  readonly hasCriteria = computed(() => this.filterCount() > 0 || this.q().trim() !== '');

  private last: InvitationQuery | null = null;
  private readonly query = computed<InvitationQuery>(() => ({
    q: this.q(),
    role: this.role(),
    invitedBy: this.invitedBy(),
    from: this.from(),
    to: this.to(),
    sort: this.sort(),
    desc: this.desc(),
    page: this.page(),
  }));

  constructor() {
    this.typed.pipe(debounceTime(300), map((text) => text.trim()), takeUntilDestroyed()).subscribe((text) => {
      if (text !== this.q()) {
        this.q.set(text);
        this.page.set(0);
      }
    });
    this.queries
      .pipe(
        tap((query) => {
          this.last = query;
          this.loading.set(true);
        }),
        // A newer search replaces one still on its way.
        switchMap((query) => this.invitations.search(query).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        if (result === null) {
          this.failed.set(true);
        } else if (result.content.length === 0 && result.page > 0 && result.totalPages > 0 && this.last) {
          // That was the last invitation of its page: show the one before.
          this.queries.next({ ...this.last, page: result.totalPages - 1 });
          return;
        } else {
          this.failed.set(false);
          this.items.set(result.content);
          this.paging.set({ page: result.page, totalPages: result.totalPages, first: result.first, last: result.last });
        }
        this.loading.set(false);
      });
    effect(() => {
      const query = this.query();
      untracked(() => this.queries.next(query));
    });
  }

  /** The invitation ends within two days. */
  expiresSoon(inv: WorkspaceInvitation): boolean {
    return new Date(inv.expiresAt).getTime() - Date.now() < 2 * 86_400_000;
  }

  roleLabel(role: DocumentWorkspaceRole): string {
    return this.i18n.translate(`documentWorkspace.role.${role}`);
  }

  /** The order is chosen in a dialog, like the filters. */
  openSort(): void {
    const t = (key: string) => this.i18n.translate(key);
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(FiltersDialogComponent, {
      data: {
        title: t('workspaces.sort.label'),
        fields: [
              { key: 'sort', type: 'select', label: t('workspaces.sort.by'), options: SORTS.map((k) => ({ value: k, label: t(`invitations.sort.${k}`) })) },
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
        values: { sort: this.sort(), dir: this.desc() ? 'desc' : 'asc' },
      },
    });
    ref.closed.subscribe((result) => {
      if (!result) return;
      const key = (SORTS.includes(result['sort'] as InvitationSort) ? result['sort'] : 'AT') as InvitationSort;
      this.sort.set(key);
      this.desc.set(result['dir'] ? result['dir'] === 'desc' : key === 'AT');
      this.page.set(0);
    });
  }

  clearAll(): void {
    this.q.set('');
    this.role.set('');
    this.invitedBy.set('');
    this.from.set('');
    this.to.set('');
    this.page.set(0);
  }

  openFilters(): void {
    this.filtersOpen.set(true);
    const t = (key: string) => this.i18n.translate(key);
    const roles: SelectOption[] = [
      { value: '', label: t('invitations.filter.anyRole') },
      ...ROLES.map((r) => ({ value: r, label: this.roleLabel(r) })),
    ];
    const fields: FilterField[] = [
      { key: 'role', type: 'select', label: t('invitations.filter.role'), options: roles },
      {
        key: 'invitedBy',
        type: 'search',
        label: t('invitations.filter.by'),
        placeholder: t('shared.filter.searchPerson'),
        emptyText: t('shared.filter.noMatches'),
        labels: this.inviterLabels,
        search: (text) => this.invitations.inviters(text),
      },
    ];
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(FiltersDialogComponent, {
      data: {
        title: t('documentWorkspaces.filters'),
        groups: [
          { label: t('workspaces.filter.tab.general'), fields },
          {
            label: t('workspaces.filter.tab.dates'),
            fields: [],
            dateRanges: [{ fromKey: 'from', toKey: 'to', fromLabel: t('shared.filter.from'), toLabel: t('shared.filter.to') }],
          },
        ],
        values: { role: this.role(), invitedBy: this.invitedBy(), from: this.from(), to: this.to() },
      },
    });
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (!result) return;
      this.role.set((result['role'] ?? '') as DocumentWorkspaceRole | '');
      this.invitedBy.set(result['invitedBy'] ?? '');
      this.from.set(result['from'] ?? '');
      this.to.set(result['to'] ?? '');
      this.page.set(0);
    });
  }

  accept(inv: WorkspaceInvitation): void {
    this.busy.set(inv.id);
    this.invitations.accept(inv.id).subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.success(this.i18n.translate('invitations.accepted', { name: inv.workspaceName }));
        // The workspace list has to learn about the new workspace before it is opened.
        this.workspaces.loadAll().subscribe({
          next: () => this.router.navigate(['/document-workspaces', inv.workspaceId]),
          error: () => this.router.navigate(['/document-workspaces']),
        });
      },
      error: (err: { message?: string }) => {
        this.busy.set(null);
        this.toast.error(err.message ?? this.i18n.translate('invitations.error'));
        this.reload();
      },
    });
  }

  decline(inv: WorkspaceInvitation): void {
    this.busy.set(inv.id);
    this.invitations.decline(inv.id).subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.success(this.i18n.translate('invitations.declined', { name: inv.workspaceName }));
        this.reload();
      },
      error: (err: { message?: string }) => {
        this.busy.set(null);
        this.toast.error(err.message ?? this.i18n.translate('invitations.error'));
      },
    });
  }

  private reload(): void {
    if (this.last) {
      this.queries.next(this.last);
    }
  }
}
