import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="empty">
      <div class="empty__icon">
        <app-icon [name]="icon()" [size]="26" [strokeWidth]="1.5" />
      </div>
      <h3 class="empty__title">{{ title() }}</h3>
      @if (description()) {
        <p class="empty__desc">{{ description() }}</p>
      }
      <div class="empty__action">
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        padding: var(--space-xxl) var(--space-lg);
        max-width: 380px;
        margin-inline: auto;
      }
      .empty__icon {
        width: 56px;
        height: 56px;
        display: grid;
        place-items: center;
        border-radius: var(--radius-lg);
        background: var(--surface-sunken);
        color: var(--text-subtle);
        margin-bottom: var(--space-md);
      }
      .empty__title {
        font-family: var(--font-text);
        font-size: var(--type-body-strong-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
        color: var(--text);
      }
      .empty__desc {
        margin-top: var(--space-xs);
        color: var(--text-muted);
        font-size: var(--type-caption-size);
        line-height: 1.5;
      }
      .empty__action:not(:empty) {
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class EmptyStateComponent {
  readonly icon = input<IconName>('inbox');
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
