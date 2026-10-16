import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'dark' | 'text' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  host: {
    '[class.btn-host--block]': 'block()',
  },
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      [attr.aria-busy]="loading()"
      class="btn"
      [class]="'btn--' + variant() + ' btn--' + size()"
      [class.btn--block]="block()"
      [class.btn--icon-only]="iconOnly()"
    >
      @if (loading()) {
        <span class="btn__spinner" aria-hidden="true"></span>
      } @else if (icon()) {
        <app-icon [name]="icon()!" [size]="iconSize()" />
      }
      @if (!iconOnly()) {
        <span class="btn__label"><ng-content /></span>
      }
    </button>
  `,
  styleUrl: './button.component.scss',
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly icon = input<IconName | undefined>(undefined);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly loading = input(false, { transform: booleanAttribute });
  readonly block = input(false, { transform: booleanAttribute });
  readonly iconOnly = input(false, { transform: booleanAttribute });

  iconSize(): number {
    return this.size() === 'sm' ? 16 : this.size() === 'lg' ? 20 : 18;
  }
}
