import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

let uid = 0;

@Component({
  selector: 'app-text-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TextFieldComponent),
      multi: true,
    },
  ],
  template: `
    @if (label()) {
      <label class="tf__label" [attr.for]="id">{{ label() }}</label>
    }
    <div class="tf__box" [class.tf__box--invalid]="invalid()" [class.tf__box--disabled]="disabledS()">
      @if (icon()) {
        <app-icon class="tf__icon" [name]="icon()!" [size]="16" />
      }
      <input
        [id]="id"
        [type]="resolvedType()"
        [value]="value"
        [placeholder]="placeholder()"
        [disabled]="disabledS()"
        [attr.autocomplete]="autocomplete()"
        [attr.aria-invalid]="invalid()"
        [attr.inputmode]="inputmode()"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
      @if (type() === 'password') {
        <button type="button" class="tf__reveal" (click)="toggleReveal()" tabindex="-1"
          [attr.aria-label]="(reveal() ? 'common.hidePassword' : 'common.showPassword') | t">
          <app-icon [name]="reveal() ? 'eye-off' : 'eye'" [size]="16" />
        </button>
      }
    </div>
    @if (invalid() && error()) {
      <p class="tf__error">{{ error() }}</p>
    } @else if (hint()) {
      <p class="tf__hint">{{ hint() }}</p>
    }
  `,
  styleUrl: './text-field.component.scss',
})
export class TextFieldComponent implements ControlValueAccessor {
  readonly id = `tf-${uid++}`;

  readonly label = input<string>('');
  readonly placeholder = input<string>('');
  readonly type = input<'text' | 'email' | 'password' | 'url' | 'number' | 'search'>('text');
  readonly icon = input<IconName | undefined>(undefined);
  readonly hint = input<string>('');
  readonly error = input<string>('');
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly autocomplete = input<string>('off');
  readonly inputmode = input<string | undefined>(undefined);

  protected value = '';
  protected readonly disabledS = signal(false);
  protected readonly reveal = signal(false);

  private onChange: (value: string) => void = () => {};
  onTouched: () => void = () => {};

  resolvedType(): string {
    return this.type() === 'password' && this.reveal() ? 'text' : this.type();
  }

  toggleReveal(): void {
    this.reveal.update((v) => !v);
  }

  onInput(event: Event): void {
    this.value = (event.target as HTMLInputElement).value;
    this.onChange(this.value);
  }

  writeValue(value: string): void {
    this.value = value ?? '';
  }
  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabledS.set(isDisabled);
  }
}
