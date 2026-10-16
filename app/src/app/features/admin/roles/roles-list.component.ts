import {ChangeDetectionStrategy, Component, OnInit, computed, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {Subject, debounceTime, distinctUntilChanged, switchMap} from 'rxjs';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {Role} from '@core/models/admin';
import {AppError} from '@core/models/api.model';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';

@Component({
  selector: 'app-roles-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    PageHeaderComponent,
    ButtonComponent,
    CardComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PaginationComponent,
    CheckboxComponent,
    TranslatePipe,
  ],
  templateUrl: './roles-list.component.html',
  styleUrl: './roles-list.component.scss',
})
export class RolesListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);

  readonly roles = signal<Role[]>([]);
  readonly loading = signal(true);
  readonly canManage = computed(() => this.auth.permissions().has('ROLE_MANAGE'));

  readonly search = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  readonly selected = signal<Set<string>>(new Set());
  readonly selectedCount = computed(() => this.selected().size);
  readonly allSelected = signal(false);
  readonly bulkBusy = signal(false);

  private readonly searchInput$ = new Subject<string>();
  private readonly refresh$ = new Subject<void>();

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((value) => {
      this.search.set(value);
      this.page.set(0);
      this.clearSelection();
      this.refresh$.next();
    });

    this.refresh$
      .pipe(
        switchMap(() => {
          this.loading.set(true);
          return this.api.listRoles({page: this.page(), size: 20, search: this.search() || undefined});
        }),
      )
      .subscribe({
        next: (result) => {
          this.roles.set(result.content);
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

  onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.refresh$.next();
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
      .listRoles({search: this.search() || undefined, page: 0, size: this.totalElements() || 10000})
      .subscribe({
        next: (result) => {
          this.bulkBusy.set(false);
          const ids = result.content.filter((r) => !r.system).map((r) => r.id);
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

  async bulkDelete(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.roles.bulk.confirmDelete.title'),
      message: this.i18n.translate('admin.roles.bulk.confirmDelete.message', {count: ids.length}),
      confirmLabel: this.i18n.translate('admin.roles.bulk.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;

    this.bulkBusy.set(true);
    this.api.bulkDeleteRoles(ids).subscribe({
      next: (result) => {
        this.bulkBusy.set(false);
        if (result.failureCount === 0) {
          this.toast.success(
            this.i18n.translate('admin.roles.bulk.toast.success', {count: result.successCount}),
          );
        } else {
          this.toast.warning(
            this.i18n.translate('admin.roles.bulk.toast.partial', {
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
