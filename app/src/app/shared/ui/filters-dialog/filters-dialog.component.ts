import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '../button/button.component';
import { IconComponent } from '../icon/icon.component';
import { CheckboxComponent } from '../checkbox/checkbox.component';
import { SelectFieldComponent, SelectOption } from '../form-field/select-field.component';
import { TextFieldComponent } from '../form-field/text-field.component';

export type FilterFieldType = 'select' | 'text' | 'number' | 'checkbox' | 'stars' | 'multiselect';

export interface FilterField {
  key: string;
  type: FilterFieldType;
  /** Already translated by the caller — this component has no i18n dictionary of its own. */
  label: string;
  options?: SelectOption[];
  placeholder?: string;
  /** 'stars' only — highest star selectable. Defaults to 5. */
  maxStars?: number;
}

export interface FilterDateRange {
  fromKey: string;
  toKey: string;
  fromLabel: string;
  toLabel: string;
}

export interface FilterGroup {
  /** Already translated by the caller. */
  label: string;
  fields: FilterField[];
}

export interface FiltersDialogData {
  /** Already translated by the caller. */
  title: string;
  /** Flat list — every field shown at once. Omit in favor of `groups` for a
   *  larger set of filters split into paginated tabs instead. */
  fields?: FilterField[];
  /** Splits the fields into tabs, one visible at a time, for pages with many
   *  filters. Mutually exclusive with `fields`. */
  groups?: FilterGroup[];
  dateRange?: FilterDateRange;
  /** Current value of every field/date key, all as strings; a checkbox is 'true' or ''. */
  values: Record<string, string>;
}

export type FiltersDialogResult = Record<string, string>;

/**
 * Field-config-driven filters modal, opened from a page's filter button
 * exactly like `AuditFiltersDialogComponent`, but shared across every page
 * instead of hand-built per page — pass the fields, get the same look.
 */
@Component({
  selector: 'app-filters-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    ButtonComponent,
    IconComponent,
    CheckboxComponent,
    SelectFieldComponent,
    TextFieldComponent,
    TranslatePipe,
  ],
  template: `
    <div class="fd" [class.fd--wide]="hasGroups()">
      <div class="fd__header">
        <h2 class="fd__title">{{ data.title }}</h2>
        <button type="button" class="fd__close" (click)="ref.close()" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>

      @if (hasGroups()) {
        <div class="fd__tabs" role="tablist">
          @for (group of data.groups; track group.label; let index = $index) {
            <button
              type="button"
              role="tab"
              class="fd__tab"
              [class.fd__tab--active]="activeGroup() === index"
              [attr.aria-selected]="activeGroup() === index"
              (click)="activeGroup.set(index)"
            >
              {{ group.label }}
            </button>
          }
        </div>
      }

      <div class="fd__body">
        @for (field of currentFields(); track field.key) {
          <ng-container [ngTemplateOutlet]="fieldTpl" [ngTemplateOutletContext]="{ $implicit: field }" />
        }

        @if (data.dateRange; as range) {
          <div class="fd__dates">
            <div class="fd__date-field">
              <label class="fd__date-label" [attr.for]="fromId">{{ range.fromLabel }}</label>
              <input [id]="fromId" class="fd__date-input" type="date" [formControl]="stringControl(range.fromKey)" />
            </div>
            <div class="fd__date-field">
              <label class="fd__date-label" [attr.for]="toId">{{ range.toLabel }}</label>
              <input [id]="toId" class="fd__date-input" type="date" [formControl]="stringControl(range.toKey)" />
            </div>
          </div>
        }
      </div>

      <div class="fd__footer">
        <app-button variant="ghost" (click)="clear()">{{ 'common.filters.clear' | t }}</app-button>
        <app-button variant="primary" (click)="apply()">{{ 'common.filters.apply' | t }}</app-button>
      </div>
    </div>

    <ng-template #fieldTpl let-field>
      @switch (field.type) {
        @case ('select') {
          <app-select-field
            [formControl]="stringControl(field.key)"
            [options]="field.options ?? []"
            [label]="field.label"
          />
        }
        @case ('text') {
          <app-text-field
            [formControl]="stringControl(field.key)"
            [label]="field.label"
            [placeholder]="field.placeholder ?? ''"
          />
        }
        @case ('number') {
          <app-text-field
            type="number"
            inputmode="decimal"
            [formControl]="stringControl(field.key)"
            [label]="field.label"
            [placeholder]="field.placeholder ?? ''"
          />
        }
        @case ('checkbox') {
          <app-checkbox [formControl]="boolControl(field.key)" [label]="field.label" />
        }
        @case ('stars') {
          <div class="fd__field">
            <label class="fd__field-label">{{ field.label }}</label>
            <div class="fd__stars" role="group" [attr.aria-label]="field.label">
              @for (star of starsRange(field.maxStars); track star) {
                <button
                  type="button"
                  class="fd__star"
                  [class.fd__star--active]="star <= starsValue(field.key)"
                  [attr.aria-label]="star"
                  (click)="setStars(field.key, star)"
                >
                  <app-icon [name]="star <= starsValue(field.key) ? 'star-filled' : 'star'" [size]="20" />
                </button>
              }
            </div>
          </div>
        }
        @case ('multiselect') {
          <div class="fd__field">
            <label class="fd__field-label">{{ field.label }}</label>
            <div class="fd__chips">
              @for (option of visibleOptions(field); track option.value) {
                <button
                  type="button"
                  class="fd__chip"
                  [class.fd__chip--active]="isMultiSelected(field.key, option.value)"
                  (click)="toggleMultiSelect(field.key, option.value)"
                >
                  {{ option.label }}
                </button>
              }
            </div>
            @if (hasMoreOptions(field)) {
              <button type="button" class="fd__chips-more" (click)="loadMoreOptions(field.key)">
                <span class="fd__chips-more-label">{{ 'common.filters.loadMore' | t }}</span>
                <span class="fd__chips-more-count">
                  {{
                    'common.filters.showingCount'
                      | t: { shown: visibleOptions(field).length, total: (field.options ?? []).length }
                  }}
                </span>
              </button>
            }
          </div>
        }
      }
    </ng-template>
  `,
  styles: [
    `
      @use 'mixins' as *;

      .fd {
        width: min(92vw, 480px);
        display: flex;
        flex-direction: column;
      }

      .fd--wide {
        width: min(92vw, 640px);
      }

      .fd__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--space-lg) var(--space-lg) 0;
      }

      .fd__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
      }

      .fd__close {
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

      .fd__tabs {
        display: flex;
        gap: 4px;
        margin: var(--space-md) var(--space-lg) 0;
        padding: 4px;
        border-radius: var(--radius-md);
        background: var(--surface-sunken);
        overflow-x: auto;
        @include scrollbar-thin;
      }

      .fd__tab {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 7px 14px;
        border: none;
        border-radius: var(--radius-sm);
        background: transparent;
        font-family: var(--font-text);
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text-muted);
        white-space: nowrap;
        cursor: pointer;
        transition: background var(--duration-fast) var(--ease-standard),
        color var(--duration-fast) var(--ease-standard);

        &:hover:not(.fd__tab--active) {
          color: var(--text);
        }

        &--active {
          background: var(--surface);
          color: var(--text);
          box-shadow: var(--shadow-sm, 0 1px 2px rgb(0 0 0 / 8%));
        }

        &:focus-visible {
          outline: none;
          box-shadow: var(--focus-ring);
        }
      }

      .fd__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        padding: var(--space-lg);
        max-height: 70vh;
        overflow-y: auto;
        @include scrollbar-thin;
      }

      .fd__field {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }

      .fd__field-label {
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }

      .fd__stars {
        display: flex;
        gap: 4px;
      }

      .fd__star {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        border: none;
        border-radius: var(--radius-sm);
        background: none;
        color: var(--text-subtle);
        cursor: pointer;

        &:hover {
          color: var(--warning);
        }
      }

      .fd__star--active {
        color: var(--warning);
      }

      .fd__chips {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }

      .fd__chip {
        padding: 6px 14px;
        border: 1px solid var(--border);
        border-radius: var(--radius-full, 999px);
        background: none;
        color: var(--text);
        font-size: var(--type-caption-size);
        font-weight: 500;
        cursor: pointer;

        &:hover {
          border-color: var(--accent);
          color: var(--accent);
        }
      }

      .fd__chip--active {
        border-color: var(--accent);
        background: color-mix(in srgb, var(--accent) 15%, transparent);
        color: var(--accent);
      }

      .fd__chips-more {
        display: flex;
        flex-direction: column;
        align-items: center;
        align-self: center;
        gap: 2px;
        margin-top: var(--space-sm);
        padding: 8px var(--space-lg);
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        background: var(--surface);
        cursor: pointer;
        font-family: var(--font-text);
        transition: background var(--duration-fast) var(--ease-standard),
          border-color var(--duration-fast) var(--ease-standard);

        &:hover {
          background: var(--surface-sunken);
          border-color: var(--border-strong);
        }

        &:focus-visible {
          outline: none;
          box-shadow: var(--focus-ring);
        }
      }

      .fd__chips-more-label {
        color: var(--accent);
        font-size: var(--type-caption-size);
        font-weight: 600;
      }

      .fd__chips-more-count {
        color: var(--text-subtle);
        font-size: var(--type-fine-size);
      }

      .fd__dates {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--space-sm);
      }

      .fd__date-field {
        display: flex;
        flex-direction: column;
      }

      .fd__date-label {
        margin-bottom: 6px;
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }

      .fd__date-input {
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

      .fd__footer {
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
export class FiltersDialogComponent {
  readonly ref = inject<DialogRef<FiltersDialogResult | undefined>>(DialogRef);
  readonly data = inject<FiltersDialogData>(DIALOG_DATA);

  readonly fromId = 'fd-from';
  readonly toId = 'fd-to';

  readonly activeGroup = signal(0);

  private static readonly MULTISELECT_PAGE_SIZE = 24;

  private readonly stringControls = new Map<string, FormControl<string>>();
  private readonly boolControls = new Map<string, FormControl<boolean>>();
  private readonly multiselectVisibleCounts = new Map<string, number>();

  constructor() {
    for (const field of this.allFields()) {
      if (field.type === 'checkbox') {
        this.boolControl(field.key);
      } else {
        this.stringControl(field.key);
      }
    }
  }

  private allFields(): FilterField[] {
    if (this.data.groups) {
      return this.data.groups.flatMap((group) => group.fields);
    }
    return this.data.fields ?? [];
  }

  hasGroups(): boolean {
    return !!this.data.groups?.length;
  }

  currentFields(): FilterField[] {
    if (this.data.groups?.length) {
      return this.data.groups[this.activeGroup()]?.fields ?? [];
    }
    return this.data.fields ?? [];
  }

  stringControl(key: string): FormControl<string> {
    let control = this.stringControls.get(key);
    if (!control) {
      control = new FormControl<string>(this.data.values[key] ?? '', { nonNullable: true });
      this.stringControls.set(key, control);
    }
    return control;
  }

  boolControl(key: string): FormControl<boolean> {
    let control = this.boolControls.get(key);
    if (!control) {
      control = new FormControl<boolean>(this.data.values[key] === 'true', { nonNullable: true });
      this.boolControls.set(key, control);
    }
    return control;
  }

  starsRange(maxStars: number | undefined): number[] {
    const max = maxStars ?? 5;
    return Array.from({ length: max }, (_, index) => index + 1);
  }

  starsValue(key: string): number {
    return Number(this.stringControl(key).value || 0);
  }

  setStars(key: string, star: number): void {
    const control = this.stringControl(key);
    control.setValue(this.starsValue(key) === star ? '' : String(star));
  }

  visibleOptions(field: FilterField): SelectOption[] {
    return (field.options ?? []).slice(0, this.multiselectVisibleCount(field.key));
  }

  hasMoreOptions(field: FilterField): boolean {
    return (field.options?.length ?? 0) > this.multiselectVisibleCount(field.key);
  }

  loadMoreOptions(key: string): void {
    this.multiselectVisibleCounts.set(
      key,
      this.multiselectVisibleCount(key) + FiltersDialogComponent.MULTISELECT_PAGE_SIZE,
    );
  }

  private multiselectVisibleCount(key: string): number {
    return this.multiselectVisibleCounts.get(key) ?? FiltersDialogComponent.MULTISELECT_PAGE_SIZE;
  }

  isMultiSelected(key: string, value: string): boolean {
    return this.stringControl(key)
      .value.split(',')
      .filter(Boolean)
      .includes(value);
  }

  toggleMultiSelect(key: string, value: string): void {
    const control = this.stringControl(key);
    const values = control.value.split(',').filter(Boolean);
    const index = values.indexOf(value);
    if (index >= 0) {
      values.splice(index, 1);
    } else {
      values.push(value);
    }
    control.setValue(values.join(','));
  }

  clear(): void {
    const result: FiltersDialogResult = {};
    for (const key of this.stringControls.keys()) {
      result[key] = '';
    }
    for (const key of this.boolControls.keys()) {
      result[key] = '';
    }
    if (this.data.dateRange) {
      result[this.data.dateRange.fromKey] = '';
      result[this.data.dateRange.toKey] = '';
    }
    this.ref.close(result);
  }

  apply(): void {
    const result: FiltersDialogResult = {};
    for (const [key, control] of this.stringControls) {
      result[key] = control.value;
    }
    for (const [key, control] of this.boolControls) {
      result[key] = control.value ? 'true' : '';
    }
    this.ref.close(result);
  }
}
