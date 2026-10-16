import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="spinner" [style.width.px]="size()" [style.height.px]="size()"></span>`,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .spinner {
        display: inline-block;
        border-radius: var(--radius-pill);
        border: 2.5px solid color-mix(in srgb, var(--accent) 22%, transparent);
        border-top-color: var(--accent);
        animation: spin 0.7s linear infinite;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class SpinnerComponent {
  readonly size = input<number>(24);
}
