import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * Filter button. Icon-only by default (existing pages); pass `label` for a
 * labelled button and `count` to show how many filters are currently applied.
 */
@Component({
  selector: 'app-filter-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button
      type="button"
      class="ft"
      [class.ft--labelled]="!!label()"
      [class.ft--active]="open() || count() > 0"
      (click)="toggle.emit()"
      [attr.aria-label]="ariaLabel() || label()"
      [attr.aria-expanded]="open()"
      aria-haspopup="dialog"
    >
      <app-icon name="filter" [size]="16" />
      @if (label()) {
        <span class="ft__label">{{ label() }}</span>
      }
      @if (count() > 0) {
        <span class="ft__count">{{ count() }}</span>
      }
    </button>
  `,
  styles: [
    `
      :host {
        display: flex;
        align-self: center;
      }
      .ft {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        width: 44px;
        height: 44px;
        flex-shrink: 0;
        padding: 0;
        border-radius: var(--radius-md);
        border: none;
        background: var(--surface);
        box-shadow: inset 0 0 0 1px var(--border);
        color: var(--text);
        font-family: var(--font-text);
        font-size: 14px;
        font-weight: 600;
        cursor: pointer;
        transition:
          background var(--duration-fast) var(--ease-standard),
          box-shadow var(--duration-fast) var(--ease-standard);

        &:hover {
          background: var(--surface-muted);
          box-shadow: inset 0 0 0 1px var(--border-strong);
        }
        &:focus-visible {
          outline: 2px solid var(--accent-focus);
          outline-offset: 2px;
        }
      }
      .ft--labelled {
        width: auto;
        padding-inline: 16px;
      }
      .ft--active {
        box-shadow: inset 0 0 0 1.5px var(--text);
      }
      .ft__count {
        display: grid;
        place-items: center;
        min-width: 20px;
        height: 20px;
        padding-inline: 6px;
        border-radius: var(--radius-pill);
        background: var(--text);
        color: var(--surface);
        font-size: 12px;
        font-weight: 700;
        line-height: 1;
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class FilterToggleComponent {
  readonly open = input(false);
  readonly ariaLabel = input('');
  /** Visible text next to the icon; omit for the icon-only button. */
  readonly label = input('');
  /** Number of filters currently applied; shown as a badge when above zero. */
  readonly count = input(0);
  readonly toggle = output<void>();
}
