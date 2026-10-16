import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  host: {
    class: 'badge',
    '[class]': '"badge--" + tone()',
  },
  styles: [
    `
      @use 'mixins' as *;

      :host {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 9px;
        border-radius: var(--radius-pill);
        font-family: var(--font-text);
        font-size: var(--type-fine-size);
        font-weight: 600;
        letter-spacing: -0.12px;
        line-height: 1.5;
        white-space: nowrap;

        @include below($bp-phone) {
          padding: 2px 7px;
          font-size: 11px;
        }
      }
      :host(.badge--neutral) {
        background: var(--surface-sunken);
        color: var(--text-muted);
      }
      :host(.badge--accent) {
        background: color-mix(in srgb, var(--accent) 12%, transparent);
        color: var(--accent);
      }
      :host(.badge--success) {
        background: color-mix(in srgb, var(--success) 14%, transparent);
        color: var(--success);
      }
      :host(.badge--warning) {
        background: color-mix(in srgb, var(--warning) 16%, transparent);
        color: var(--warning);
      }
      :host(.badge--danger) {
        background: color-mix(in srgb, var(--danger) 12%, transparent);
        color: var(--danger);
      }
    `,
  ],
})
export class BadgeComponent {
  readonly tone = input<BadgeTone>('neutral');
}
