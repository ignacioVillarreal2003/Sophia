import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  booleanAttribute,
  forwardRef,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { IconComponent } from '../icon/icon.component';

export interface SelectOption {
  value: string;
  label: string;
}

let uid = 0;

@Component({
  selector: 'app-select-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, CdkMenu, CdkMenuItem, CdkMenuTrigger],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SelectFieldComponent), multi: true },
  ],
  template: `
    @if (label()) {
      <label class="sf__label" [attr.for]="id">{{ label() }}</label>
    }
    <button
      #trigger
      type="button"
      [id]="id"
      class="sf__trigger"
      [class.sf__trigger--placeholder]="!selectedLabel()"
      [class.sf__trigger--open]="menuOpen()"
      [disabled]="disabledS()"
      [cdkMenuTriggerFor]="panel"
      (cdkMenuOpened)="menuOpen.set(true)"
      (cdkMenuClosed)="menuOpen.set(false); onTouched()"
    >
      <span class="sf__value">{{ selectedLabel() ?? placeholder() }}</span>
      <app-icon class="sf__chevron" [class.sf__chevron--open]="menuOpen()" name="chevron-down" [size]="16" />
    </button>

    <ng-template #panel>
      <div class="menu sf__panel" cdkMenu [style.width.px]="panelWidth()">
        @for (opt of options(); track opt.value) {
          <button type="button" class="menu__item" cdkMenuItem (click)="select(opt.value)">
            <span>{{ opt.label }}</span>
            @if (opt.value === value) {
              <span class="menu__item-trailing"><app-icon name="check" [size]="14" /></span>
            }
          </button>
        }
      </div>
    </ng-template>
  `,
  styles: [
    `
      :host { display: block; }
      .sf__label {
        display: block;
        margin-bottom: 6px;
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }
      .sf__trigger {
        display: flex;
        align-items: center;
        width: 100%;
        height: 44px;
        padding: 0 40px 0 16px;
        background: var(--surface);
        border: none;
        border-radius: var(--radius-md);
        box-shadow: inset 0 0 0 1px var(--border);
        font-family: var(--font-text);
        font-size: var(--type-body-size);
        color: var(--text);
        text-align: left;
        cursor: pointer;
        outline: none;
        position: relative;
        transition: box-shadow var(--duration-fast) var(--ease-standard);
        &:hover:not(:disabled) { box-shadow: inset 0 0 0 1px var(--border-strong); }
        &:focus-visible,
        &--open {
          box-shadow: inset 0 0 0 1.5px var(--accent-focus),
            0 0 0 3px color-mix(in srgb, var(--accent-focus) 15%, transparent);
        }
        &:disabled { cursor: not-allowed; opacity: 0.6; }
      }
      .sf__value {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .sf__trigger--placeholder .sf__value {
        color: var(--text-subtle);
      }
      .sf__chevron {
        position: absolute;
        right: 14px;
        top: 50%;
        transform: translateY(-50%);
        color: var(--text-subtle);
        pointer-events: none;
        transition: transform var(--duration-fast) var(--ease-standard);
        &--open { transform: translateY(-50%) rotate(180deg); }
      }
      .sf__panel {
        min-width: 0;
        max-width: none;
        max-height: 280px;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: var(--border-strong) transparent;
        &::-webkit-scrollbar {
          width: 8px;
          height: 8px;
        }
        &::-webkit-scrollbar-thumb {
          background: var(--border-strong);
          border-radius: var(--radius-pill);
        }
        &::-webkit-scrollbar-track {
          background: transparent;
        }
      }
    `,
  ],
})
export class SelectFieldComponent implements ControlValueAccessor, AfterViewInit, OnDestroy {
  readonly id = `sf-${uid++}`;
  readonly label = input<string>('');
  readonly placeholder = input<string>('');
  readonly options = input.required<SelectOption[]>();
  readonly required = input(false, { transform: booleanAttribute });

  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  private resizeObserver: ResizeObserver | null = null;

  protected value = '';
  protected readonly disabledS = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly panelWidth = signal(0);
  private onChange: (v: string) => void = () => {};
  onTouched: () => void = () => {};

  protected selectedLabel(): string | null {
    return this.options().find((opt) => opt.value === this.value)?.label ?? null;
  }

  ngAfterViewInit(): void {
    const el = this.trigger()?.nativeElement;
    if (!el) return;
    this.panelWidth.set(el.offsetWidth);
    this.resizeObserver = new ResizeObserver(() => this.panelWidth.set(el.offsetWidth));
    this.resizeObserver.observe(el);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  select(value: string): void {
    this.value = value;
    this.onChange(this.value);
  }
  writeValue(value: string): void {
    this.value = value ?? '';
  }
  registerOnChange(fn: (v: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledS.set(isDisabled);
  }
}
