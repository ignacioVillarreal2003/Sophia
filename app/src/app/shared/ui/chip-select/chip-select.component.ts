import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';

const PAGE_SIZE = 24;

export interface ChipOption {
  value: string;
  label: string;
  hint?: string;
}

@Component({
  selector: 'app-chip-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ChipSelectComponent), multi: true },
  ],
  imports: [TranslatePipe],
  template: `
    @if (label()) {
      <span class="cs__label">{{ label() }}</span>
    }
    <div class="cs__chips">
      @for (opt of visibleOptions(); track opt.value) {
        <button
          type="button"
          class="cs__chip"
          [class.cs__chip--active]="isSelected(opt.value)"
          [disabled]="disabledS()"
          [attr.title]="opt.hint || null"
          (click)="toggle(opt.value)"
        >
          {{ opt.label }}
        </button>
      } @empty {
        <span class="cs__empty">{{ 'chipSelect.noOptions' | t }}</span>
      }
    </div>
    @if (hasMore()) {
      <button type="button" class="cs__more" (click)="loadMore()">
        <span class="cs__more-label">{{ 'common.filters.loadMore' | t }}</span>
        <span class="cs__more-count">
          {{ 'common.filters.showingCount' | t: { shown: visibleOptions().length, total: options().length } }}
        </span>
      </button>
    }
  `,
  styles: [
    `
      @use 'mixins' as *;

      :host {
        display: block;
      }
      .cs__label {
        display: block;
        margin-bottom: 6px;
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }
      .cs__chips {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-xs);
      }
      .cs__empty {
        color: var(--text-subtle);
        font-size: var(--type-caption-size);
      }
      .cs__chip {
        padding: 6px 13px;
        border-radius: var(--radius-pill);
        border: 1px solid var(--border);
        background: var(--surface);
        color: var(--text-muted);
        font-family: var(--font-text);
        font-size: 13px;
        font-weight: 600;
        letter-spacing: -0.12px;
        cursor: pointer;
        transition: border-color var(--duration-fast) var(--ease-standard);
        &:hover:not(:disabled):not(.cs__chip--active) {
          border-color: var(--border-strong);
        }
        &:disabled {
          cursor: not-allowed;
          opacity: 0.5;
        }

        @include below($bp-phone) {
          padding: 5px 10px;
          font-size: 11px;
        }
      }
      .cs__chip--active {
        background: color-mix(in srgb, var(--accent) 12%, transparent);
        border-color: var(--accent);
        color: var(--accent);
      }

      .cs__more {
        display: flex;
        flex-direction: column;
        align-items: center;
        align-self: flex-start;
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

      .cs__more-label {
        color: var(--accent);
        font-size: var(--type-caption-size);
        font-weight: 600;
      }

      .cs__more-count {
        color: var(--text-subtle);
        font-size: var(--type-fine-size);
      }
    `,
  ],
})
export class ChipSelectComponent implements ControlValueAccessor {
  readonly label = input<string>('');
  readonly options = input.required<ChipOption[]>();
  readonly disabled = input(false, { transform: booleanAttribute });

  protected value: string[] = [];
  protected readonly disabledS = signal(false);
  private readonly visibleCount = signal(PAGE_SIZE);

  private onChange: (value: string[]) => void = () => {};
  onTouched: () => void = () => {};

  readonly visibleOptions = computed(() => this.options().slice(0, this.visibleCount()));
  readonly hasMore = computed(() => this.visibleCount() < this.options().length);

  loadMore(): void {
    this.visibleCount.update((count) => count + PAGE_SIZE);
  }

  isSelected(value: string): boolean {
    return this.value.includes(value);
  }

  toggle(value: string): void {
    if (this.disabledS()) {
      return;
    }
    this.value = this.isSelected(value)
      ? this.value.filter((v) => v !== value)
      : [...this.value, value];
    this.onChange(this.value);
    this.onTouched();
  }

  writeValue(value: string[] | null): void {
    this.value = value ?? [];
  }
  registerOnChange(fn: (value: string[]) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledS.set(isDisabled);
  }
}
