import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslationService } from '@core/services/i18n';
import { ButtonComponent } from '../button/button.component';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  /** Info-only mode: nothing to cancel, so only the confirm button shows. */
  hideCancel?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent],
  template: `
    <div class="confirm">
      <h2 class="confirm__title">{{ data.title }}</h2>
      <p class="confirm__message">{{ data.message }}</p>
      <div class="confirm__actions">
        @if (!data.hideCancel) {
          <app-button variant="ghost" (click)="ref.close(false)">
            {{ data.cancelLabel ?? i18n.translate('common.cancel') }}
          </app-button>
        }
        <app-button [variant]="data.danger ? 'danger' : 'primary'" (click)="ref.close(true)">
          {{ data.confirmLabel ?? i18n.translate('common.confirm') }}
        </app-button>
      </div>
    </div>
  `,
  styles: [
    `
      .confirm {
        padding: var(--space-lg);
        width: min(90vw, 420px);
      }
      .confirm__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
      }
      .confirm__message {
        margin-top: var(--space-xs);
        color: var(--text-muted);
        line-height: 1.5;
        font-size: var(--type-body-size);
      }
      .confirm__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class ConfirmDialogComponent {
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly data = inject<ConfirmDialogData>(DIALOG_DATA);
  readonly i18n = inject(TranslationService);
}
