import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { SpinnerComponent } from '../spinner/spinner.component';

@Component({
  selector: 'app-load-more-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, SpinnerComponent, TranslatePipe],
  template: `
    <button
      type="button"
      class="lmb"
      [disabled]="loading()"
      (click)="clicked.emit()"
      [attr.aria-label]="'common.loadMore' | t"
      [attr.title]="'common.loadMore' | t"
    >
      @if (loading()) {
        <app-spinner [size]="16" />
      } @else {
        <app-icon name="chevron-down" [size]="18" />
      }
    </button>
  `,
  styles: [
    `
      :host {
        display: flex;
        justify-content: center;
      }
      .lmb {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        border-radius: var(--radius-pill);
        background: var(--surface-sunken);
        color: var(--text);

        &:hover:not(:disabled) {
          background: var(--surface-raised);
        }

        &:disabled {
          cursor: not-allowed;
          opacity: 0.6;
        }
      }
    `,
  ],
})
export class LoadMoreButtonComponent {
  readonly loading = input(false, { transform: booleanAttribute });
  readonly clicked = output<void>();
}
