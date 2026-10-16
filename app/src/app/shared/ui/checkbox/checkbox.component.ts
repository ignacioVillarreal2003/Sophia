import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  effect,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-checkbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CheckboxComponent), multi: true },
  ],
  template: `
    <span class="cb" [class.cb--disabled]="isDisabled()">
      <button
        type="button"
        role="checkbox"
        class="cb__box"
        [class.cb__box--checked]="value"
        [attr.aria-checked]="value"
        [attr.aria-label]="label() || null"
        [disabled]="isDisabled()"
        (click)="toggle()"
      >
        @if (value) {
          <app-icon name="check" [size]="11" class="cb__check" />
        }
      </button>
      @if (label()) {
        <span class="cb__label" (click)="toggle()">{{ label() }}</span>
      }
    </span>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
      }
      .cb {
        display: inline-flex;
        align-items: center;
        gap: 8px;
      }
      .cb__box {
        display: grid;
        place-items: center;
        width: 20px;
        height: 20px;
        flex-shrink: 0;
        box-sizing: border-box;
        border-radius: 50%;
        border: none;
        padding: 0;
        background: var(--surface);
        box-shadow: inset 0 0 0 1.5px var(--border);
        cursor: pointer;
        transition: background var(--duration-fast) var(--ease-standard),
          box-shadow var(--duration-fast) var(--ease-standard),
          transform var(--duration-fast) var(--ease-standard);

        &:hover:not(:disabled) {
          box-shadow: inset 0 0 0 1.5px var(--border-strong);
        }
        &:active:not(:disabled) {
          transform: scale(0.88);
        }
        &:focus-visible {
          outline: none;
          box-shadow: inset 0 0 0 1.5px var(--accent-focus),
            0 0 0 3px color-mix(in srgb, var(--accent-focus) 22%, transparent);
        }
        &:disabled {
          opacity: 0.4;
          cursor: not-allowed;
        }
      }
      .cb__box--checked {
        background: var(--accent);
        box-shadow: none;
        &:hover:not(:disabled) {
          background: var(--accent-hover);
        }
      }
      .cb__check {
        color: var(--on-accent);
      }
      .cb__label {
        font-size: var(--type-caption-size);
        color: var(--text);
        cursor: pointer;
        user-select: none;
      }
      .cb--disabled .cb__label {
        opacity: 0.5;
        cursor: not-allowed;
      }
    `,
  ],
})
export class CheckboxComponent implements ControlValueAccessor {
  readonly label = input<string>('');
  readonly checked = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly checkedChange = output<boolean>();

  protected value = false;
  private readonly disabledS = signal(false);
  private cvaBound = false;
  private onChangeFn: (v: boolean) => void = () => {};
  onTouched: () => void = () => {};

  constructor() {
    effect(() => {
      const next = this.checked();
      if (!this.cvaBound) {
        this.value = next;
      }
    });
  }

  protected isDisabled(): boolean {
    return this.disabledS() || this.disabled();
  }

  protected toggle(): void {
    if (this.isDisabled()) return;
    this.value = !this.value;
    this.onChangeFn(this.value);
    this.checkedChange.emit(this.value);
    this.onTouched();
  }

  writeValue(value: boolean): void {
    this.cvaBound = true;
    this.value = !!value;
  }
  registerOnChange(fn: (v: boolean) => void): void {
    this.onChangeFn = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledS.set(isDisabled);
  }
}
