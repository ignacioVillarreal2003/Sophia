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
import {FormControl, ReactiveFormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {Observable, Subject, debounceTime, distinctUntilChanged, firstValueFrom, switchMap} from 'rxjs';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {User} from '@core/models/authentication';
import {Permission, Role, UserListQuery} from '@core/models/admin';
import {AppError, BulkOperationResponse} from '@core/models/api.model';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SelectFieldComponent, SelectOption} from '@shared/ui/form-field/select-field.component';
import {ChipOption} from '@shared/ui/chip-select/chip-select.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';
import {FilterToggleComponent} from '@shared/ui/filter-toggle/filter-toggle.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {BulkChipsDialogComponent, BulkChipsDialogData} from './bulk-chips-dialog.component';

@Component({
  selector: 'app-users-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    CardComponent,
    AvatarComponent,
    BadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PaginationComponent,
    IconComponent,
    SelectFieldComponent,
    CheckboxComponent,
    FilterToggleComponent,
    TranslatePipe,
  ],
  templateUrl: './users-list.component.html',
  styleUrl: './users-list.component.scss',
})
export class UsersListComponent implements OnInit, AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly users = signal<User[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);
  readonly availableRoles = signal<Role[]>([]);
  readonly availablePermissions = signal<Permission[]>([]);

  private readonly searchInputEl = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly search = signal('');
  readonly rawSearch = signal('');
  readonly activeFilter = new FormControl<string>('', {nonNullable: true});
  readonly roleFilter = new FormControl<string>('', {nonNullable: true});
  readonly emailVerifiedFilter = new FormControl<string>('', {nonNullable: true});

  readonly canCreate = computed(() => this.auth.permissions().has('USER_CREATE'));
  readonly canDelete = computed(() => this.auth.permissions().has('USER_DELETE'));
  readonly canUpdateStatus = computed(() => this.auth.permissions().has('USER_UPDATE'));
  readonly canManageRoles = computed(() => this.auth.permissions().has('ROLE_MANAGE'));

  readonly selected = signal<Set<string>>(new Set());
  readonly selectedCount = computed(() => this.selected().size);
  readonly allSelected = signal(false);
  readonly bulkBusy = signal(false);

  readonly roleChipOptions = computed<ChipOption[]>(() =>
    this.availableRoles().map((r) => ({value: r.name, label: r.name, hint: r.description ?? ''})),
  );

  readonly permissionChipOptions = computed<ChipOption[]>(() =>
    this.availablePermissions().map((p) => ({value: p.name, label: p.name, hint: p.description ?? ''})),
  );

  readonly activeOptions = computed<SelectOption[]>(() => [
    {value: '', label: this.i18n.translate('admin.users.filter.allStatuses')},
    {value: 'true', label: this.i18n.translate('admin.users.filter.active')},
    {value: 'false', label: this.i18n.translate('admin.users.filter.inactive')},
  ]);

  readonly roleOptions = computed<SelectOption[]>(() => [
    {value: '', label: this.i18n.translate('admin.users.filter.allRoles')},
    ...this.availableRoles().map((r) => ({value: r.name, label: r.name})),
  ]);

  readonly emailVerifiedOptions = computed<SelectOption[]>(() => [
    {value: '', label: this.i18n.translate('admin.users.filter.allVerification')},
    {value: 'true', label: this.i18n.translate('admin.users.emailVerified')},
    {value: 'false', label: this.i18n.translate('admin.users.emailUnverified')},
  ]);

  readonly filtersOpen = signal(false);

  toggleFilters(): void {
    this.filtersOpen.update((v) => !v);
  }

  hasActiveFilters(): boolean {
    return !!this.activeFilter.value || !!this.roleFilter.value || !!this.emailVerifiedFilter.value;
  }

  private readonly searchInput$ = new Subject<string>();
  private readonly refresh$ = new Subject<void>();

  ngOnInit(): void {
    this.restoreFromUrl();

    this.api.listRoles({size: 200}).subscribe({
      next: (page) => this.availableRoles.set(page.content),
      error: (err: AppError) => this.toast.error(err.message),
    });
    this.api.listPermissions({size: 200}).subscribe({
      next: (page) => this.availablePermissions.set(page.content),
      error: (err: AppError) => this.toast.error(err.message),
    });

    this.searchInput$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((value) => {
      this.search.set(value);
      this.page.set(0);
      this.clearSelection();
      this.refresh$.next();
    });

    this.activeFilter.valueChanges.subscribe(() => {
      this.page.set(0);
      this.clearSelection();
      this.refresh$.next();
    });

    this.roleFilter.valueChanges.subscribe(() => {
      this.page.set(0);
      this.clearSelection();
      this.refresh$.next();
    });

    this.emailVerifiedFilter.valueChanges.subscribe(() => {
      this.page.set(0);
      this.clearSelection();
      this.refresh$.next();
    });

    this.refresh$
      .pipe(
        switchMap(() => {
          const query = this.buildQuery();
          this.syncUrl(query);
          this.loading.set(true);
          return this.api.listUsers(query);
        }),
      )
      .subscribe({
        next: (result) => {
          this.users.set(result.content);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
          this.first.set(result.first);
          this.last.set(result.last);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });

    this.refresh$.next();
  }

  ngAfterViewInit(): void {
    const el = this.searchInputEl()?.nativeElement;
    if (el) {
      el.value = this.rawSearch();
    }
  }

  private restoreFromUrl(): void {
    const params = this.route.snapshot.queryParamMap;
    const initialSearch = params.get('q') ?? '';
    const page = Number(params.get('page'));

    this.search.set(initialSearch);
    this.rawSearch.set(initialSearch);
    this.page.set(Number.isFinite(page) && page > 0 ? page : 0);
    this.activeFilter.setValue(params.get('active') ?? '', {emitEvent: false});
    this.emailVerifiedFilter.setValue(params.get('verified') ?? '', {emitEvent: false});
    this.roleFilter.setValue(params.get('role') ?? '', {emitEvent: false});
    this.filtersOpen.set(this.hasActiveFilters());
  }

  private syncUrl(query: UserListQuery): void {
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        q: query.search ?? null,
        active: query.active === undefined ? null : String(query.active),
        verified: query.emailVerified === undefined ? null : String(query.emailVerified),
        role: query.role ?? null,
        page: query.page ? query.page : null,
      },
    });
  }

  onSearchInput(value: string): void {
    this.rawSearch.set(value);
    this.searchInput$.next(value);
  }

  clearSearch(): void {
    this.rawSearch.set('');
    this.search.set('');
    const el = this.searchInputEl()?.nativeElement;
    if (el) {
      el.value = '';
      el.focus();
    }
    this.page.set(0);
    this.clearSelection();
    this.refresh$.next();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.refresh$.next();
  }

  private buildQuery(): UserListQuery {
    return {
      page: this.page(),
      size: 20,
      sort: 'createdAt,desc',
      search: this.search() || undefined,
      active: this.activeFilter.value === '' ? undefined : this.activeFilter.value === 'true',
      emailVerified:
        this.emailVerifiedFilter.value === '' ? undefined : this.emailVerifiedFilter.value === 'true',
      role: this.roleFilter.value || undefined,
    };
  }

  isSelf(id: string): boolean {
    return this.auth.user()?.id === id;
  }

  isSelected(id: string): boolean {
    return this.selected().has(id);
  }

  toggleSelect(id: string): void {
    const next = new Set(this.selected());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selected.set(next);
    this.allSelected.set(false);
  }

  toggleSelectAll(): void {
    if (this.allSelected()) {
      this.clearSelection();
      return;
    }
    this.bulkBusy.set(true);
    this.api
      .listUsers({...this.buildQuery(), page: 0, size: this.totalElements() || 10000})
      .subscribe({
        next: (result) => {
          this.bulkBusy.set(false);
          const ids = result.content.filter((u) => !this.isSelf(u.id)).map((u) => u.id);
          this.selected.set(new Set(ids));
          this.allSelected.set(true);
        },
        error: (err: AppError) => {
          this.bulkBusy.set(false);
          this.toast.error(err.message);
        },
      });
  }

  clearSelection(): void {
    this.selected.set(new Set());
    this.allSelected.set(false);
  }

  async bulkActivate(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.bulk.confirmActivate.title'),
      message: this.i18n.translate('admin.users.bulk.confirmActivate.message', {count: ids.length}),
      confirmLabel: this.i18n.translate('admin.users.bulk.confirmActivate.confirmLabel'),
    });
    if (!ok) return;
    this.runBulk(this.api.bulkUpdateUserStatus(ids, true));
  }

  async bulkDeactivate(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.bulk.confirmDeactivate.title'),
      message: this.i18n.translate('admin.users.bulk.confirmDeactivate.message', {count: ids.length}),
      confirmLabel: this.i18n.translate('admin.users.bulk.confirmDeactivate.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.runBulk(this.api.bulkUpdateUserStatus(ids, false));
  }

  async bulkDelete(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.users.bulk.confirmDelete.title'),
      message: this.i18n.translate('admin.users.bulk.confirmDelete.message', {count: ids.length}),
      confirmLabel: this.i18n.translate('admin.users.bulk.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.runBulk(this.api.bulkDeleteUsers(ids));
  }

  async openRolesDialog(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ref = this.dialog.open<BulkChipsDialogComponent, BulkChipsDialogData, string[] | undefined>(
      BulkChipsDialogComponent,
      {
        data: {
          title: this.i18n.translate('admin.users.bulk.setRoles'),
          hint: this.i18n.translate('admin.users.bulk.setRolesHint'),
          options: this.roleChipOptions(),
          applyLabel: this.i18n.translate('admin.users.bulk.apply'),
        },
      },
    );
    const roles = await firstValueFrom(ref.closed);
    if (!roles || roles.length === 0) return;
    this.runBulk(this.api.bulkUpdateUserRoles(ids, roles));
  }

  async openPermissionsDialog(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ref = this.dialog.open<BulkChipsDialogComponent, BulkChipsDialogData, string[] | undefined>(
      BulkChipsDialogComponent,
      {
        data: {
          title: this.i18n.translate('admin.users.bulk.addPermissions'),
          hint: this.i18n.translate('admin.users.bulk.addPermissionsHint'),
          options: this.permissionChipOptions(),
          applyLabel: this.i18n.translate('admin.users.bulk.apply'),
        },
      },
    );
    const added = await firstValueFrom(ref.closed);
    if (!added || added.length === 0) return;
    this.runBulk(this.api.bulkAddUserPermissions(ids, added));
  }

  private runBulk(request$: Observable<BulkOperationResponse>): void {
    this.bulkBusy.set(true);
    request$.subscribe({
      next: (result) => {
        this.bulkBusy.set(false);
        if (result.failureCount === 0) {
          this.toast.success(this.i18n.translate('admin.users.bulk.toast.success', {count: result.successCount}));
        } else {
          this.toast.warning(
            this.i18n.translate('admin.users.bulk.toast.partial', {
              done: result.successCount,
              total: result.successCount + result.failureCount,
            }),
          );
        }
        this.clearSelection();
        this.refresh$.next();
      },
      error: (err: AppError) => {
        this.bulkBusy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
