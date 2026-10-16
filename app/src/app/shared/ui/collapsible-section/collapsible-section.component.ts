import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  input,
  model,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

let nextId = 0;

/**
 * A titled section that expands on click, so a long form page can be scanned
 * as a list of headings and opened one part at a time.
 *
 * <p>The body is only rendered while open: nothing inside it loads, or asks
 * the server for anything, until the section is actually opened.
 */
@Component({
  selector: 'app-collapsible-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button
      type="button"
      class="cs__head"
      [id]="headerId"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="bodyId"
      (click)="toggle()"
    >
      @if (icon(); as name) {
        <span class="cs__icon"><app-icon [name]="name" [size]="19"/></span>
      }
      <span class="cs__text">
        <span class="cs__title">{{ title() }}</span>
        @if (hint()) {
          <span class="cs__hint">{{ hint() }}</span>
        }
      </span>
      <span class="cs__chevron" [class.cs__chevron--open]="open()">
        <app-icon name="chevron-down" [size]="18"/>
      </span>
    </button>

    @if (open()) {
      <div
        class="cs__body"
        [class.cs__body--flush]="flush()"
        [id]="bodyId"
        role="region"
        [attr.aria-labelledby]="headerId"
      >
        <ng-content/>
      </div>
    }
  `,
  host: {
    class: 'cs',
    '[class.cs--open]': 'open()',
  },
  styles: [
    `
      :host {
        display: block;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
        overflow: hidden;
      }

      :host(.cs--open) {
        border-color: var(--border-strong);
      }

      .cs__head {
        display: flex;
        align-items: center;
        gap: var(--space-sm);
        width: 100%;
        padding: var(--space-md) var(--space-lg);
        background: none;
        border: none;
        text-align: left;
        cursor: pointer;
        color: inherit;

        &:hover {
          background: var(--surface-sunken);
        }

        &:focus-visible {
          outline: none;
          box-shadow: var(--focus-ring);
        }
      }

      .cs__icon {
        display: grid;
        place-items: center;
        width: 38px;
        height: 38px;
        flex-shrink: 0;
        border-radius: var(--radius-pill);
        background: var(--surface-sunken);
        color: var(--accent);
      }

      .cs__text {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
      }

      .cs__title {
        font-size: var(--type-body-strong-size);
        font-weight: 600;
        color: var(--text);
      }

      .cs__hint {
        font-size: var(--type-caption-size);
        color: var(--text-muted);
        line-height: 1.45;
      }

      .cs__chevron {
        display: grid;
        place-items: center;
        flex-shrink: 0;
        color: var(--text-muted);
        transition: transform var(--duration-fast) var(--ease-standard);
      }

      .cs__chevron--open {
        transform: rotate(180deg);
      }

      .cs__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-sm);
        padding: var(--space-lg);
        border-top: 1px solid var(--divider);
      }

      .cs__body--flush {
        padding: 0;
      }
    `,
  ],
})
export class CollapsibleSectionComponent {
  readonly title = input.required<string>();
  readonly hint = input<string>('');
  readonly icon = input<IconName | undefined>(undefined);
  /** Removes the body padding for sections that draw their own rows. */
  readonly flush = input(false, { transform: booleanAttribute });

  readonly open = model(false);

  private readonly uid = nextId++;
  readonly headerId = `collapsible-head-${this.uid}`;
  readonly bodyId = `collapsible-body-${this.uid}`;

  toggle(): void {
    this.open.update((open) => !open);
  }
}
