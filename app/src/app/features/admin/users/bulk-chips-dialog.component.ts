import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {FormControl, ReactiveFormsModule} from '@angular/forms';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {TranslationService} from '@core/services/i18n';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {ChipSelectComponent, ChipOption} from '@shared/ui/chip-select/chip-select.component';

export interface BulkChipsDialogData {
  title: string;
  hint: string;
  options: ChipOption[];
  applyLabel: string;
}

@Component({
  selector: 'app-bulk-chips-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, ChipSelectComponent],
  template: `
    <div class="bcd">
      <div class="bcd__header">
        <h2 class="bcd__title">{{ data.title }}</h2>
        <p class="bcd__hint">{{ data.hint }}</p>
      </div>
      <div class="bcd__body">
        <app-chip-select [formControl]="control" [options]="data.options"/>
      </div>
      <div class="bcd__actions">
        <app-button variant="ghost" (click)="ref.close()">{{ i18n.translate('common.cancel') }}</app-button>
        <app-button [disabled]="!control.value.length" (click)="ref.close(control.value)">
          {{ data.applyLabel }}
        </app-button>
      </div>
    </div>
  `,
  styles: [
    `
      @use 'mixins' as *;

      .bcd {
        display: flex;
        flex-direction: column;
        width: min(90vw, 480px);
        max-height: 90vh;
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      .bcd__header {
        flex-shrink: 0;
      }

      .bcd__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
      }

      .bcd__hint {
        margin-top: var(--space-xs);
        color: var(--text-muted);
        line-height: 1.5;
        font-size: var(--type-body-size);
      }

      .bcd__body {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        margin-top: var(--space-md);
        @include scrollbar-thin;
      }

      .bcd__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
        margin-top: var(--space-lg);
        flex-shrink: 0;
      }

      app-chip-select {
        display: block;
      }
    `,
  ],
})
export class BulkChipsDialogComponent {
  readonly ref = inject<DialogRef<string[] | undefined>>(DialogRef);
  readonly data = inject<BulkChipsDialogData>(DIALOG_DATA);
  readonly i18n = inject(TranslationService);
  readonly control = new FormControl<string[]>([], {nonNullable: true});
}
