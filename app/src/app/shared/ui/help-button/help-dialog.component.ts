import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslationService } from '@core/services/i18n';
import { ButtonComponent } from '../button/button.component';

export interface HelpDialogData {
  title: string;
  paragraphs: string[];
}

@Component({
  selector: 'app-help-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent],
  template: `
    <div class="help">
      <h2 class="help__title">{{ data.title }}</h2>
      <div class="help__body">
        @for (paragraph of data.paragraphs; track $index) {
          <p class="help__paragraph">{{ paragraph }}</p>
        }
      </div>
      <div class="help__actions">
        <app-button variant="primary" (click)="ref.close()">{{ i18n.translate('common.gotIt') }}</app-button>
      </div>
    </div>
  `,
  styles: [
    `
      .help {
        padding: var(--space-lg);
        width: min(90vw, 460px);
      }
      .help__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
        color: var(--text-strong);
      }
      .help__body {
        margin-top: var(--space-sm);
        display: flex;
        flex-direction: column;
        gap: var(--space-sm);
      }
      .help__paragraph {
        margin: 0;
        color: var(--text-muted);
        line-height: 1.5;
        font-size: var(--type-body-size);
      }
      .help__actions {
        display: flex;
        justify-content: flex-end;
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class HelpDialogComponent {
  readonly ref = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<HelpDialogData>(DIALOG_DATA);
  readonly i18n = inject(TranslationService);
}
