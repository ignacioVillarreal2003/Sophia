import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { DocumentWorkspace, DocumentWorkspaceListQuery, DocumentWorkspaceRole } from '@core/models/document-workspace';
import { WorkspaceLogoComponent } from '../ui/workspace-logo.component';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
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

const ROLES: DocumentWorkspaceRole[] = ['ADMIN', 'EDITOR', 'VIEWER'];
const PAGE_SIZE = 20;

/** Full document-workspace list — entry it replaces the old inline switcher in
 *  the sidebar's "Espacios" section, with search + role/status filters and
 *  real server-side pagination, same as the admin list pages
 *  (see users-list.component.ts for the pattern this mirrors). */
@Component({
  selector: 'app-document-workspaces-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    PageHeaderComponent,
    ButtonComponent,
    CardComponent,
    WorkspaceLogoComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    FilterToggleComponent,
    PaginationComponent,
    TranslatePipe,
  ],
  templateUrl: './document-workspaces-list.component.html',
  styleUrl: './document-workspaces-list.component.scss',
})
export class DocumentWorkspacesListComponent implements OnInit, AfterViewInit {
  private readonly documentWorkspacesService = inject(DocumentWorkspaceService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);

  private readonly searchInputEl = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly documentWorkspaces = signal<DocumentWorkspace[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  readonly search = signal('');
  readonly roleFilter = new FormControl<string>('', { nonNullable: true });
  readonly filtersOpen = signal(false);
  readonly filterCount = signal(0);


  readonly roleOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.i18n.translate('documentWorkspaces.filter.allRoles') },
    ...ROLES.map((r) => ({ value: r, label: this.i18n.translate(`documentWorkspace.role.${r}`) })),
  ]);

  private readonly searchInput$ = new Subject<string>();
  private readonly refresh$ = new Subject<void>();

  ngOnInit(): void {
    this.restoreFromUrl();

    this.searchInput$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((value) => {
      this.search.set(value);
      this.page.set(0);
      this.refresh$.next();
    });

    this.roleFilter.valueChanges.subscribe(() => {
      this.updateFilterCount();
      this.page.set(0);
      this.refresh$.next();
    });

    this.refresh$
      .pipe(
        switchMap(() => {
          const query = this.buildQuery();
          this.syncUrl(query);
          this.loading.set(true);
          return this.documentWorkspacesService.search(query);
        }),
      )
      .subscribe({
        next: (result) => {
          this.documentWorkspaces.set(result.content);
          this.totalPages.set(result.totalPages);
          this.first.set(result.first);
          this.last.set(result.last);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });

    this.refresh$.next();
  }

  ngAfterViewInit(): void {
    // The search box is an uncontrolled input (no [value] binding, so typing
    // fast isn't fought by the debounced `search` signal) — seed it manually
    // once from whatever the URL restored into `search`.
    const el = this.searchInputEl()?.nativeElement;
    if (el) {
      el.value = this.search();
    }
  }

  /** Reads filters/page back out of the current URL so a reload (or sharing
   *  the link) lands on the exact same filtered view instead of resetting. */
  private restoreFromUrl(): void {
    const params = this.route.snapshot.queryParamMap;
    const page = Number(params.get('page'));

    this.search.set(params.get('q') ?? '');
    this.page.set(Number.isFinite(page) && page > 0 ? page : 0);
    this.roleFilter.setValue(params.get('role') ?? '', { emitEvent: false });
    this.updateFilterCount();
  }

  /** Mirrors the current query into the URL (replacing, not pushing history)
   *  so every fetch leaves the address bar reload-able back to that state. */
  private syncUrl(query: DocumentWorkspaceListQuery): void {
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        q: query.search ?? null,
        role: query.role ?? null,
        page: query.page ? query.page : null,
      },
    });
  }

  private buildQuery(): DocumentWorkspaceListQuery {
    return {
      page: this.page(),
      size: PAGE_SIZE,
      search: this.search() || undefined,
      role: (this.roleFilter.value || undefined) as DocumentWorkspaceRole | undefined,
    };
  }

  onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  /** Opens the shared filters modal; applying it refreshes the list once. */
  openFilters(): void {
    this.filtersOpen.set(true);
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(
      FiltersDialogComponent,
      {
        data: {
          title: this.i18n.translate('documentWorkspaces.filters'),
          fields: [
            {
              key: 'role',
              type: 'select',
              label: this.i18n.translate('documentWorkspaces.filter.role'),
              options: this.roleOptions(),
            },
          ],
          values: { role: this.roleFilter.value },
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (!result) return;
      this.roleFilter.setValue(result['role'] ?? '', { emitEvent: false });
      this.updateFilterCount();
      this.page.set(0);
      this.refresh$.next();
    });
  }

  private updateFilterCount(): void {
    this.filterCount.set(this.roleFilter.value ? 1 : 0);
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.refresh$.next();
  }

  open(ws: DocumentWorkspace): void {
    this.router.navigate(['/document-workspaces', ws.id]);
  }

  roleLabel(role: DocumentWorkspaceRole): string {
    return this.i18n.translate(`documentWorkspace.role.${role}`);
  }
}
