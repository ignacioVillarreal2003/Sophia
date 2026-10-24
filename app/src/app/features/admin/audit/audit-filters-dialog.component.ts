import {ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, signal} from '@angular/core';
import {FormControl, ReactiveFormsModule} from '@angular/forms';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {Subject, of, switchMap} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {User} from '@core/models/authentication';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SelectFieldComponent, SelectOption} from '@shared/ui/form-field/select-field.component';
import type {AuditSource} from './audit.component';

export interface AuditFiltersValues {
  action: string;
  resourceType: string;
  status: string;
  from: string;
  to: string;
  workspace: string;
  subjectId: string;
  subjectLabel: string;
}

export interface AuditFiltersDialogData {
  source: AuditSource;
  showStatusFilter: boolean;
  showWorkspaceFilter: boolean;
  showSubjectFilter: boolean;
  actionOptions: SelectOption[];
  resourceTypeOptions: SelectOption[];
  statusOptions: SelectOption[];
  workspaceOptions: SelectOption[];
  values: AuditFiltersValues;
}

@Component({
  selector: 'app-audit-filters-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    AvatarComponent,
    ButtonComponent,
    IconComponent,
    SelectFieldComponent,
    TranslatePipe,
  ],
  template: `
    <div class="afd">
      <div class="afd__header">
        <h2 class="afd__title">{{ 'admin.audit.filtersDialog.title' | t }}</h2>
        <button type="button" class="afd__close" (click)="ref.close()" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>

      <div class="afd__body">
        <app-select-field
          [formControl]="action"
          [options]="data.actionOptions"
          [label]="'admin.audit.filter.action' | t"
        />
        <app-select-field
          [formControl]="resourceType"
          [options]="data.resourceTypeOptions"
          [label]="'admin.audit.filter.resourceType' | t"
        />
        @if (data.showStatusFilter) {
          <app-select-field
            [formControl]="status"
            [options]="data.statusOptions"
            [label]="'admin.audit.filter.status' | t"
          />
        }
        @if (data.showWorkspaceFilter) {
          <app-select-field
            [formControl]="workspace"
            [options]="data.workspaceOptions"
            [label]="'admin.audit.filter.workspace' | t"
          />
        }
        @if (data.showSubjectFilter) {
          <div class="afd__field">
            <span class="afd__date-label">{{ 'admin.audit.filter.subject' | t }}</span>
            @if (subjectLabel()) {
              <div class="afd__subject-chip">
                <span class="afd__subject-chip-text">{{ subjectLabel() }}</span>
                <button
                  type="button"
                  class="afd__chip-remove"
                  (click)="clearSubject()"
                  [attr.aria-label]="'admin.audit.removeFilter' | t"
                >
                  <app-icon name="x" [size]="12" />
                </button>
              </div>
            } @else {
              <div class="afd__subject-search">
                <input
                  #subjectInput
                  class="afd__date-input"
                  type="search"
                  [placeholder]="'admin.audit.searchUserPlaceholder' | t"
                  [value]="subjectSearchTerm()"
                  (input)="onSubjectInput($any($event.target).value)"
                  (keydown.enter)="submitSubjectSearch(subjectSearchTerm())"
                />
                @if (subjectSearchOpen() && (subjectSearchLoading() || subjectSearchResults().length > 0)) {
                  <div class="afd__subject-results">
                    @if (subjectSearchLoading()) {
                      <div class="afd__subject-status">{{ 'admin.audit.searching' | t }}</div>
                    } @else if (subjectSearchResults().length === 0) {
                      <div class="afd__subject-status">{{ 'admin.audit.noUsersFound' | t }}</div>
                    } @else {
                      @for (user of subjectSearchResults(); track user.id) {
                        <button type="button" class="afd__subject-result" (click)="selectSubject(user)">
                          <app-avatar [name]="user.fullName" [photoUrl]="user.photoUrl" [size]="24" />
                          <span class="afd__subject-result-text">
                            <span class="afd__subject-result-name">{{ user.fullName }}</span>
                            <span class="afd__subject-result-email">{{ user.email }}</span>
                          </span>
                        </button>
                      }
                    }
                  </div>
                }
              </div>
            }
          </div>
        }
        <div class="afd__dates">
          <div class="afd__date-field">
            <label class="afd__date-label" for="afdFrom">{{ 'admin.audit.filter.from' | t }}</label>
            <input id="afdFrom" class="afd__date-input" type="date" [formControl]="from" />
          </div>
          <div class="afd__date-field">
            <label class="afd__date-label" for="afdTo">{{ 'admin.audit.filter.to' | t }}</label>
            <input id="afdTo" class="afd__date-input" type="date" [formControl]="to" />
          </div>
        </div>
      </div>

      <div class="afd__footer">
        <app-button variant="ghost" (click)="clear()">{{ 'admin.audit.filtersDialog.clear' | t }}</app-button>
        <app-button variant="dark" (click)="apply()">{{ 'admin.audit.filtersDialog.apply' | t }}</app-button>
      </div>
    </div>
  `,
  styles: [
    `
      @use 'mixins' as *;

      .afd {
        width: min(92vw, 560px);
        display: flex;
        flex-direction: column;
      }

      .afd__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--space-lg) var(--space-lg) 0;
      }

      .afd__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
      }

      .afd__close {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        flex-shrink: 0;
        border: none;
        border-radius: var(--radius-pill);
        background: var(--surface-sunken);
        color: var(--text-muted);
        cursor: pointer;
        transition: background var(--duration-fast) var(--ease-standard);

        &:hover {
          background: var(--surface-raised);
        }
      }

      .afd__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        padding: var(--space-lg);
        max-height: 70vh;
        overflow-y: auto;
        @include scrollbar-thin;
      }

      .afd__field {
        display: flex;
        flex-direction: column;
      }

      .afd__dates {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--space-sm);
      }

      .afd__date-field {
        display: flex;
        flex-direction: column;
      }

      .afd__date-label {
        margin-bottom: 6px;
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }

      .afd__date-input {
        width: 100%;
        height: 44px;
        padding: 0 14px;
        background: var(--surface);
        border: none;
        border-radius: var(--radius-md);
        box-shadow: inset 0 0 0 1px var(--border);
        font-family: var(--font-text);
        font-size: var(--type-body-size);
        color: var(--text);
        outline: none;
        transition: box-shadow var(--duration-fast) var(--ease-standard);

        &:hover:not(:focus) {
          box-shadow: inset 0 0 0 1px var(--border-strong);
        }

        &:focus {
          box-shadow: inset 0 0 0 1.5px var(--accent-focus),
          0 0 0 3px color-mix(in srgb, var(--accent) 15%, transparent);
        }
      }

      .afd__subject-search {
        position: relative;
      }

      .afd__subject-chip {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-sm);
        height: 44px;
        padding: 0 8px 0 14px;
        border-radius: var(--radius-md);
        background: color-mix(in srgb, var(--accent) 10%, transparent);
      }

      .afd__subject-chip-text {
        min-width: 0;
        font-size: var(--type-body-size);
        color: var(--text);
        @include truncate;
      }

      .afd__chip-remove {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 22px;
        height: 22px;
        flex-shrink: 0;
        padding: 0;
        border: none;
        border-radius: 50%;
        background: none;
        color: var(--text-muted);
        cursor: pointer;
        transition: background var(--duration-fast) var(--ease-standard);

        &:hover {
          background: var(--surface-raised);
        }
      }

      .afd__subject-results {
        position: absolute;
        top: calc(100% + 6px);
        left: 0;
        right: 0;
        z-index: 20;
        max-height: 220px;
        overflow-y: auto;
        padding: 6px;
        border-radius: var(--radius-md);
        background: var(--surface);
        box-shadow: var(--shadow-md, 0 8px 24px rgb(0 0 0 / 12%)), 0 0 0 1px var(--border);
        @include scrollbar-thin;
      }

      .afd__subject-status {
        padding: 10px 12px;
        font-size: var(--type-caption-size);
        color: var(--text-subtle);
      }

      .afd__subject-result {
        display: flex;
        align-items: center;
        gap: 10px;
        width: 100%;
        padding: 8px;
        border: none;
        border-radius: var(--radius-sm);
        background: none;
        text-align: left;
        cursor: pointer;
        transition: background var(--duration-fast) var(--ease-standard);

        &:hover {
          background: var(--surface-sunken);
        }
      }

      .afd__subject-result-text {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 1px;
      }

      .afd__subject-result-name {
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
        @include truncate;
      }

      .afd__subject-result-email {
        font-size: 11px;
        color: var(--text-subtle);
        @include truncate;
      }

      .afd__footer app-button ::ng-deep .btn {
        height: 34px;
        padding-inline: 15px;
        font-size: var(--type-caption-size);
        border-radius: var(--radius-sm);
      }
      .afd__footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-sm);
        padding: var(--space-md) var(--space-lg) var(--space-lg);
        border-top: 1px solid var(--divider);
        margin-top: var(--space-sm);
      }
    `,
  ],
})
export class AuditFiltersDialogComponent {
  readonly ref = inject<DialogRef<AuditFiltersValues | undefined>>(DialogRef);
  readonly data = inject<AuditFiltersDialogData>(DIALOG_DATA);
  private readonly api = inject(AdminApiService);
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly action = new FormControl<string>(this.data.values.action, {nonNullable: true});
  readonly resourceType = new FormControl<string>(this.data.values.resourceType, {nonNullable: true});
  readonly status = new FormControl<string>(this.data.values.status, {nonNullable: true});
  readonly from = new FormControl<string>(this.data.values.from, {nonNullable: true});
  readonly to = new FormControl<string>(this.data.values.to, {nonNullable: true});
  readonly workspace = new FormControl<string>(this.data.values.workspace, {nonNullable: true});

  private subjectId = this.data.values.subjectId;
  readonly subjectLabel = signal(this.data.values.subjectLabel);
  readonly subjectSearchTerm = signal('');
  readonly subjectSearchResults = signal<User[]>([]);
  readonly subjectSearchLoading = signal(false);
  readonly subjectSearchOpen = signal(false);
  private readonly subjectSearch$ = new Subject<string>();

  constructor() {
    this.subjectSearch$
      .pipe(
        switchMap((term) => {
          const trimmed = term.trim();
          if (!trimmed) return of<User[]>([]);
          this.subjectSearchLoading.set(true);
          return this.api.listUsers({search: trimmed, size: 6}).pipe(
            map((page) => page.content),
            catchError(() => of<User[]>([])),
          );
        }),
      )
      .subscribe((results) => {
        this.subjectSearchLoading.set(false);
        this.subjectSearchResults.set(results);
        this.subjectSearchOpen.set(true);
      });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.subjectSearchOpen()) return;
    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.subjectSearchOpen.set(false);
    }
  }

  onSubjectInput(value: string): void {
    this.subjectSearchTerm.set(value);
    if (!value.trim()) {
      this.subjectSearchResults.set([]);
      this.subjectSearchOpen.set(false);
    }
  }

  submitSubjectSearch(term: string): void {
    if (!term.trim()) {
      this.subjectSearchResults.set([]);
      this.subjectSearchOpen.set(false);
      return;
    }
    this.subjectSearchLoading.set(true);
    this.subjectSearch$.next(term);
  }

  selectSubject(user: User): void {
    this.subjectId = user.id;
    this.subjectLabel.set(user.email);
    this.subjectSearchTerm.set('');
    this.subjectSearchResults.set([]);
    this.subjectSearchOpen.set(false);
  }

  clearSubject(): void {
    this.subjectId = '';
    this.subjectLabel.set('');
  }

  clear(): void {
    this.ref.close({
      action: '',
      resourceType: '',
      status: '',
      from: '',
      to: '',
      workspace: this.workspace.value,
      subjectId: '',
      subjectLabel: '',
    });
  }

  apply(): void {
    this.ref.close({
      action: this.action.value,
      resourceType: this.resourceType.value,
      status: this.status.value,
      from: this.from.value,
      to: this.to.value,
      workspace: this.workspace.value,
      subjectId: this.subjectId,
      subjectLabel: this.subjectLabel(),
    });
  }
}
