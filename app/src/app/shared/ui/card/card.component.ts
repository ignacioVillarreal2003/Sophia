import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, inject, input } from '@angular/core';

@Component({
  selector: 'app-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  host: {
    class: 'card',
    '[class.card--interactive]': 'interactive()',
    '[class.card--flush]': 'flush()',
    '[attr.tabindex]': 'interactive() ? 0 : null',
    '[attr.role]': 'interactive() ? "button" : null',
    '(keydown.enter)': 'activateFromKeyboard($event)',
    '(keydown.space)': 'activateFromKeyboard($event)',
  },
  styles: [
    `
      :host {
        display: block;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
        padding: var(--space-lg);
      }
      :host(.card--flush) {
        padding: 0;
        overflow: hidden;
      }
      :host(.card--interactive) {
        cursor: pointer;
        transition: transform var(--duration-fast) var(--ease-standard),
          border-color var(--duration-fast) var(--ease-standard);
      }
      :host(.card--interactive:hover) {
        border-color: var(--border-strong);
      }
      :host(.card--interactive:active) {
        transform: scale(0.99);
      }
      :host(.card--interactive:focus-visible) {
        outline: none;
        box-shadow: var(--focus-ring);
      }
    `,
  ],
})
export class CardComponent {
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly interactive = input(false, { transform: booleanAttribute });
  readonly flush = input(false, { transform: booleanAttribute });

  activateFromKeyboard(event: Event): void {
    if (!this.interactive()) {
      return;
    }
    event.preventDefault();
    this.elementRef.nativeElement.click();
  }
}
