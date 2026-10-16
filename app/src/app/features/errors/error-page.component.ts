import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';

@Component({
  selector: 'app-error-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ButtonComponent, EmptyStateComponent, TranslatePipe],
  template: `
    <div class="err">
      <app-empty-state icon="alert-circle" [title]="titleKey() | t" [description]="descriptionKey() | t">
        @if (actionLink()) {
          <app-button variant="secondary" [routerLink]="actionLink()">{{ actionLabelKey() | t }}</app-button>
        } @else {
          <app-button variant="secondary" (click)="back()">{{ actionLabelKey() | t }}</app-button>
        }
      </app-empty-state>
    </div>
  `,
  styles: [
    `
      .err {
        min-height: 100dvh;
        display: flex;
        align-items: center;
        justify-content: center;
      }
    `,
  ],
})
export class ErrorPageComponent {
  private readonly location = inject(Location);

  readonly titleKey = input.required<string>();
  readonly descriptionKey = input.required<string>();
  readonly actionLabelKey = input.required<string>();
  readonly actionLink = input<string>('');

  back(): void {
    this.location.back();
  }
}
