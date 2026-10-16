import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent } from '../button/button.component';
import { TranslationService } from '@core/services/i18n';

@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ButtonComponent],
  template: `
    <div class="ph">
      <div class="ph__heading">
        @if (backLink()) {
          <app-button
            class="ph__back"
            variant="primary"
            size="sm"
            icon="arrow-left"
            iconOnly
            [routerLink]="backLink()"
            [queryParams]="backQueryParams()"
            [attr.aria-label]="backLabel()"
          />
        }
        <div class="ph__text">
          <h1 class="ph__title">{{ title() }}</h1>
          @if (subtitle()) {
            <p class="ph__subtitle">{{ subtitle() }}</p>
          }
        </div>
      </div>
      <div class="ph__actions">
        <ng-content />
      </div>
    </div>
  `,
  styles: [
    `
      .ph {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: var(--space-md);
        flex-wrap: wrap;
        margin-bottom: var(--space-lg);
      }
      .ph__heading {
        display: flex;
        align-items: center;
        gap: var(--space-md);
      }
      .ph__back {
        flex-shrink: 0;
      }
      .ph__title {
        font-family: var(--font-display);
        font-size: var(--type-display-md-size);
        font-weight: 600;
        line-height: 1.1;
        letter-spacing: -0.02em;
        color: var(--text);
      }
      .ph__subtitle {
        margin-top: 6px;
        color: var(--text-muted);
        font-size: var(--type-body-size);
      }
      .ph__actions {
        display: flex;
        align-items: center;
        gap: var(--space-xs);
      }
    `,
  ],
})
export class PageHeaderComponent {
  private readonly i18n = inject(TranslationService);

  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
  readonly backLink = input<string | undefined>(undefined);
  readonly backQueryParams = input<Record<string, string | number> | undefined>(undefined);

  backLabel(): string {
    return this.i18n.translate('common.back');
  }
}
