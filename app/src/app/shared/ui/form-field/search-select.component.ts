import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { IconComponent } from '../icon/icon.component';
import { SelectOption } from './select-field.component';

let uid = 0;

/**
 * Picks one value out of a set too big to list: the person types, and the options come from `search` (which asks the
 * server for the few that match). The label of what was picked is remembered in `labels`, so the field can show it
 * again when it is reopened.
 */
@Component({
  selector: 'app-search-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SearchSelectComponent), multi: true }],
  template: `
    @if (label()) {
      <label class="ss__label" [attr.for]="id">{{ label() }}</label>
    }
    @if (value(); as chosen) {
      <div class="ss__chosen">
        <span class="ss__value">{{ shown() }}</span>
        <button type="button" class="ss__clear" [attr.aria-label]="clearLabel()" (click)="clear()">
          <app-icon name="x" [size]="14" />
        </button>
      </div>
    } @else {
      <div class="ss__box">
        <app-icon name="search" [size]="15" class="ss__icon" />
        <input
          [id]="id"
          class="ss__input"
          type="text"
          autocomplete="off"
          role="combobox"
          [attr.aria-expanded]="open()"
          [placeholder]="placeholder()"
          [value]="text()"
          (input)="onInput($any($event.target).value)"
          (focus)="onFocus()"
          (blur)="onBlur()"
        />
      </div>
      @if (open()) {
        <ul class="ss__list" role="listbox">
          @for (option of results(); track option.value) {
            <li>
              <button type="button" class="ss__option" role="option" (mousedown)="$event.preventDefault()" (click)="pick(option)">
                {{ option.label }}
              </button>
            </li>
          } @empty {
            <li class="ss__empty">{{ loading() ? '…' : emptyText() }}</li>
          }
        </ul>
      }
    }
  `,
  styles: `
    :host { display: block; }
    .ss__label { display: block; margin-bottom: 6px; font-size: var(--type-caption-size); font-weight: 600; color: var(--text); }
    .ss__box, .ss__chosen {
      position: relative; display: flex; align-items: center; height: 44px; background: var(--surface);
      border-radius: var(--radius-md); box-shadow: inset 0 0 0 1px var(--border);
    }
    .ss__box:focus-within { box-shadow: inset 0 0 0 1.5px var(--accent-focus), 0 0 0 3px color-mix(in srgb, var(--accent-focus) 15%, transparent); }
    .ss__icon { position: absolute; left: 14px; color: var(--text-subtle); pointer-events: none; }
    .ss__input { width: 100%; height: 100%; padding: 0 16px 0 38px; font: inherit; font-size: var(--type-body-size); color: var(--text); background: transparent; border: 0; outline: none; }
    .ss__chosen { justify-content: space-between; gap: 8px; padding: 0 8px 0 16px; font-size: var(--type-body-size); color: var(--text); }
    .ss__value { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ss__clear { display: grid; place-items: center; flex: none; width: 28px; height: 28px; color: var(--text-subtle); background: transparent; border: 0; border-radius: var(--radius-sm); cursor: pointer; &:hover { color: var(--text); background: var(--surface-muted); } }
    .ss__list { margin: 6px 0 0; padding: 4px; max-height: 200px; overflow-y: auto; list-style: none; background: var(--surface); border-radius: var(--radius-md); box-shadow: inset 0 0 0 1px var(--border); }
    .ss__option { display: block; width: 100%; padding: 9px 12px; font: inherit; font-size: var(--type-caption-size); color: var(--text); text-align: left; background: transparent; border: 0; border-radius: var(--radius-sm); cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; &:hover, &:focus-visible { background: var(--surface-muted); outline: none; } }
    .ss__empty { padding: 10px 12px; font-size: var(--type-caption-size); color: var(--text-subtle); }
  `,
})
export class SearchSelectComponent implements ControlValueAccessor {
  readonly label = input('');
  readonly placeholder = input('');
  readonly emptyText = input('');
  readonly clearLabel = input('Clear');
  readonly search = input.required<(text: string) => Observable<SelectOption[]>>();
  /** Labels of the values picked so far, kept by the caller across openings. */
  readonly labels = input<Record<string, string>>({});

  protected readonly id = `ss-${++uid}`;
  protected readonly value = signal('');
  protected readonly text = signal('');
  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly results = signal<SelectOption[]>([]);
  protected readonly shown = computed(() => this.labels()[this.value()] ?? this.value());

  private readonly queries = new Subject<string>();
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    this.queries
      .pipe(
        debounceTime(200),
        tap(() => this.loading.set(true)),
        switchMap((text) => this.search()(text).pipe(catchError(() => of([] as SelectOption[])))),
        takeUntilDestroyed(),
      )
      .subscribe((options) => {
        this.results.set(options);
        this.loading.set(false);
      });
  }

  protected onFocus(): void {
    this.open.set(true);
    this.queries.next(this.text());
  }

  protected onInput(text: string): void {
    this.text.set(text);
    this.open.set(true);
    this.queries.next(text);
  }

  protected onBlur(): void {
    this.open.set(false);
    this.onTouched();
  }

  protected pick(option: SelectOption): void {
    this.labels()[option.value] = option.label;
    this.value.set(option.value);
    this.text.set('');
    this.open.set(false);
    this.onChange(option.value);
  }

  protected clear(): void {
    this.value.set('');
    this.onChange('');
  }

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
}
