import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let uid = 0;

@Component({
  selector: 'app-textarea',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextareaComponent), multi: true },
  ],
  template: `
    @if (label()) {
      <label class="ta__label" [attr.for]="id">{{ label() }}</label>
    }
    <textarea
      [id]="id"
      [value]="value"
      [rows]="rows()"
      [placeholder]="placeholder()"
      [disabled]="disabledS()"
      [class.ta--invalid]="invalid()"
      (input)="onInput($event)"
      (blur)="onTouched()"
    ></textarea>
    @if (invalid() && error()) {
      <p class="ta__error">{{ error() }}</p>
    }
  `,
  styles: [
    `
      :host { display: block; }
      .ta__label {
        display: block;
        margin-bottom: 6px;
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }
      textarea {
        width: 100%;
        padding: 12px 16px;
        background: var(--surface);
        border: none;
        border-radius: var(--radius-md);
        box-shadow: inset 0 0 0 1px var(--border);
        font-family: var(--font-text);
        font-size: var(--type-body-size);
        line-height: 1.5;
        color: var(--text);
        resize: vertical;
        outline: none;
        transition: box-shadow var(--duration-fast) var(--ease-standard);
        field-sizing: content;
        max-height: 400px;
        overflow-y: auto;
        &::placeholder { color: var(--text-subtle); }
        &:hover:not(:disabled):not(.ta--invalid):not(:focus) {
          box-shadow: inset 0 0 0 1px var(--border-strong);
        }
        &:focus {
          box-shadow: inset 0 0 0 1.5px var(--accent-focus),
            0 0 0 3px color-mix(in srgb, var(--accent-focus) 15%, transparent);
        }
      }
      .ta--invalid { box-shadow: inset 0 0 0 1.5px var(--danger); }
      .ta__error {
        margin-top: 6px;
        font-size: var(--type-caption-size);
        color: var(--danger);
      }
    `,
  ],
})
export class TextareaComponent implements ControlValueAccessor {
  readonly id = `ta-${uid++}`;
  readonly label = input<string>('');
  readonly placeholder = input<string>('');
  readonly rows = input<number>(4);
  readonly error = input<string>('');
  readonly invalid = input(false, { transform: booleanAttribute });

  protected value = '';
  protected readonly disabledS = signal(false);
  private onChange: (v: string) => void = () => {};
  onTouched: () => void = () => {};

  onInput(event: Event): void {
    this.value = (event.target as HTMLTextAreaElement).value;
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
