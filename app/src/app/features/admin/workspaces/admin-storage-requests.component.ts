import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppError } from '@core/models/api.model';
import { RequestStatus, StorageRequest } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { FileSizePipe } from '@shared/pipes/file-size.pipe';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { WorkspaceRequestsService } from '@features/document-workspace/data/workspace-extras.service';
import { bindUrlState } from '@shared/utils/url-state';

const TABS: RequestStatus[] = ['PENDING', 'APPROVED', 'DENIED'];

/** Owners who ran out of space ask here for more; an admin grants an amount or declines. */
@Component({
  selector: 'app-admin-storage-requests',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ButtonComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    SpinnerComponent,
    FileSizePipe,
    RelativeTimePipe,
    TranslatePipe,
  ],
  template: `
    <div class="sr">
      <app-page-header
        [title]="'admin.storageRequests.title' | t"
        [subtitle]="'admin.storageRequests.subtitle' | t"
        backLink="/admin/workspaces"
      />

      <div class="sr__tabs" role="tablist">
        @for (tab of tabs; track tab) {
          <button
            type="button"
            role="tab"
            class="sr__tab"
            [class.sr__tab--active]="status() === tab"
            [attr.aria-selected]="status() === tab"
            (click)="setStatus(tab)"
          >
            {{ 'admin.storageRequests.status.' + tab | t }}
          </button>
        }
      </div>

      @if (loading()) {
        <div class="sr__loading"><app-spinner /></div>
      } @else if (items().length === 0) {
        <app-empty-state icon="inbox" [title]="'admin.storageRequests.empty' | t" />
      } @else {
        <ul class="sr__list">
          @for (r of items(); track r.id) {
            <li class="sr__row">
              <div class="sr__main">
                <a class="sr__ws" [routerLink]="['/admin/workspaces', r.workspaceId]">{{ r.workspaceName }}</a>
                <span class="sr__meta">
                  {{ r.requestedByEmail || '—' }} · {{ r.createdAt | relativeTime }}
                </span>
                @if (r.message) {
                  <span class="sr__message">“{{ r.message }}”</span>
                }
              </div>
              <div class="sr__usage">
                <span class="sr__asked">+{{ label(r.requestedMb) }}</span>
                <span class="sr__meta">{{ r.usedBytes | fileSize }} / {{ r.quotaMb ? label(r.quotaMb) : '∞' }}</span>
              </div>

              @if (r.status === 'PENDING') {
                <div class="sr__actions">
                  <label class="sr__grant">
                    <span class="sr__grant-label">{{ 'admin.storageRequests.grant' | t }}</span>
                    <input
                      type="number"
                      min="1"
                      class="sr__input"
                      [value]="grantFor(r)"
                      (input)="setGrant(r, $any($event.target).value)"
                    />
                    <span class="sr__unit">MB</span>
                  </label>
                  <app-button size="sm" variant="primary" [disabled]="busyId() === r.id" (click)="approve(r)">
                    {{ 'workspaces.access.approve' | t }}
                  </app-button>
                  <app-button size="sm" variant="ghost" [disabled]="busyId() === r.id" (click)="deny(r)">
                    {{ 'workspaces.access.deny' | t }}
                  </app-button>
                </div>
              } @else {
                <span class="sr__outcome" [class.sr__outcome--ok]="r.status === 'APPROVED'">
                  {{ 'admin.storageRequests.status.' + r.status | t }}
                  @if (r.grantedMb) {
                    · +{{ label(r.grantedMb) }}
                  }
                </span>
              }
            </li>
          }
        </ul>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [first]="first()" [last]="last()" (pageChange)="onPage($event)" />
      }
    </div>
  `,
  styles: [
    `
      @use 'mixins' as *;

      .sr { padding-block: var(--space-xl); }
      .sr__tabs {
        display: inline-flex; gap: 4px; padding: 4px; margin-bottom: var(--space-lg);
        border-radius: var(--radius-md); background: var(--surface-sunken);
      }
      .sr__tab {
        padding: 7px 16px; border: none; border-radius: var(--radius-sm); background: transparent;
        color: var(--text-muted); font-family: var(--font-text); font-size: var(--type-caption-size);
        font-weight: 600; cursor: pointer;
      }
      .sr__tab--active { background: var(--surface); color: var(--text); box-shadow: 0 1px 2px rgb(0 0 0 / 8%); }
      .sr__loading { display: grid; place-items: center; padding: var(--space-section); }
      .sr__list {
        margin: 0; padding: 0; list-style: none; border: 1px solid var(--border);
        border-radius: var(--radius-lg); background: var(--surface); overflow: hidden;
      }
      .sr__row {
        display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: var(--space-md);
        padding: 16px 20px;
        &:not(:last-child) { border-bottom: 1px solid var(--divider); }
        @include below($bp-tablet-portrait) { grid-template-columns: 1fr; }
      }
      .sr__main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
      .sr__ws { font-weight: 600; color: var(--text); text-decoration: none; overflow-wrap: anywhere; }
      .sr__ws:hover { text-decoration: underline; }
      .sr__meta { font-size: var(--type-fine-size); color: var(--text-subtle); }
      .sr__message { font-size: var(--type-caption-size); color: var(--text-muted); overflow-wrap: anywhere; }
      .sr__usage { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
      .sr__asked { font-weight: 700; }
      .sr__actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
      .sr__grant { display: inline-flex; align-items: center; gap: 6px; }
      .sr__grant-label { font-size: var(--type-fine-size); color: var(--text-subtle); }
      .sr__input {
        width: 96px; height: 34px; padding: 0 10px; border: none; border-radius: var(--radius-sm);
        background: var(--surface); box-shadow: inset 0 0 0 1px var(--border); color: var(--text); font: inherit;
      }
      .sr__unit { font-size: var(--type-fine-size); color: var(--text-subtle); }
      .sr__outcome { font-size: var(--type-caption-size); font-weight: 600; color: var(--text-muted); }
      .sr__outcome--ok { color: var(--success); }
    `,
  ],
})
export class AdminStorageRequestsComponent implements OnInit {
  private readonly requests = inject(WorkspaceRequestsService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly tabs = TABS;
  readonly status = signal<RequestStatus>('PENDING');
  readonly items = signal<StorageRequest[]>([]);
  readonly loading = signal(true);
  readonly busyId = signal<string | null>(null);
  readonly page = signal(0);

  /** The address and this state are one: filters, order and page are read from it and kept in it. */
  private readonly urlState = bindUrlState([
    { signal: this.status, param: 'status', allowed: ['PENDING', 'APPROVED', 'REJECTED'] },
    { signal: this.page, param: 'page', page: true },
  ]);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);
  private readonly grants = signal<Record<string, number>>({});

  ngOnInit(): void {
    this.load();
  }

  setStatus(status: RequestStatus): void {
    if (this.status() === status) return;
    this.status.set(status);
    this.page.set(0);
    this.load();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  label(mb: number): string {
    return mb >= 1024 ? `${+(mb / 1024).toFixed(1)} GB` : `${mb} MB`;
  }

  grantFor(request: StorageRequest): number {
    return this.grants()[request.id] ?? request.requestedMb;
  }

  setGrant(request: StorageRequest, value: string): void {
    const parsed = Math.floor(Number(value));
    this.grants.update((map) => ({ ...map, [request.id]: Number.isFinite(parsed) ? parsed : 0 }));
  }

  approve(request: StorageRequest): void {
    const granted = this.grantFor(request);
    if (granted < 1) {
      this.toast.error(this.i18n.translate('admin.storageRequests.invalidAmount'));
      return;
    }
    this.busyId.set(request.id);
    this.requests.approveStorage(request.id, granted).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success(this.i18n.translate('admin.storageRequests.approved', { name: request.workspaceName }));
        this.load();
      },
      error: (err: AppError) => {
        this.busyId.set(null);
        this.toast.error(err.message);
      },
    });
  }

  deny(request: StorageRequest): void {
    this.busyId.set(request.id);
    this.requests.denyStorage(request.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.load();
      },
      error: (err: AppError) => {
        this.busyId.set(null);
        this.toast.error(err.message);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.requests.storageRequests(this.status(), this.page()).subscribe({
      next: (result) => {
        if (result.content.length === 0 && result.totalPages > 0 && this.page() > 0) {
          this.page.set(result.totalPages - 1);
          this.load();
          return;
        }
        this.items.set(result.content);
        this.totalPages.set(result.totalPages);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
