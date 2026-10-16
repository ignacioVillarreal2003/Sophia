import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {NotificationCategoryInfo} from '@core/models/notifications';
import {TranslationService} from '@core/services/i18n';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';

export interface CategoryDialogData {
  /** Absent when creating; the code becomes read-only while editing. */
  category: NotificationCategoryInfo | null;
}

export interface CategoryDialogResult {
  code: string;
  name: string;
  description: string | null;
  mandatory: boolean;
}

/** Create or edit an entry of the notification category catalogue. */
@Component({
  selector: 'app-category-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, CheckboxComponent, TextFieldComponent],
  template: `
    <form class="cdlg" [formGroup]="form" (ngSubmit)="apply()">
      <h2 class="cdlg__title">
        {{ i18n.translate(editing ? 'admin.notifications.categories.edit' : 'admin.notifications.categories.new') }}
      </h2>

      <div class="cdlg__body">
        <app-text-field
          formControlName="code"
          [label]="i18n.translate('admin.notifications.categories.code')"
          [hint]="i18n.translate('admin.notifications.categories.codeHint')"
        />
        <app-text-field
          formControlName="name"
          [label]="i18n.translate('admin.notifications.categories.name')"
        />
        <app-text-field
          formControlName="description"
          [label]="i18n.translate('admin.notifications.categories.description')"
        />
        <label class="cdlg__check">
          <app-checkbox formControlName="mandatory"/>
          <span>
            <span class="cdlg__check-label">{{ i18n.translate('admin.notifications.categories.mandatory') }}</span>
            <span class="cdlg__check-hint">{{ i18n.translate('admin.notifications.categories.mandatoryHint') }}</span>
          </span>
        </label>
      </div>

      <div class="cdlg__actions">
        <app-button variant="ghost" type="button" (click)="ref.close()">
          {{ i18n.translate('common.cancel') }}
        </app-button>
        <app-button type="submit" [disabled]="form.invalid">
          {{ i18n.translate('common.save') }}
        </app-button>
      </div>
    </form>
  `,
  styles: [
    `
      .cdlg {
        display: flex;
        flex-direction: column;
        width: min(90vw, 460px);
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      .cdlg__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .cdlg__body {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        margin-top: var(--space-md);
      }

      .cdlg__check {
        display: flex;
        align-items: flex-start;
        gap: var(--space-sm);
        cursor: pointer;
      }

      .cdlg__check-label {
        display: block;
        font-size: var(--type-body-size);
        color: var(--text);
      }

      .cdlg__check-hint {
        display: block;
        font-size: var(--type-caption-size);
        color: var(--text-muted);
      }

      .cdlg__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class CategoryDialogComponent {
  private readonly fb = inject(FormBuilder);

  readonly ref = inject<DialogRef<CategoryDialogResult | undefined>>(DialogRef);
  readonly data = inject<CategoryDialogData>(DIALOG_DATA);
  readonly i18n = inject(TranslationService);

  readonly editing = !!this.data.category;

  readonly form = this.fb.nonNullable.group({
    code: [
      {value: this.data.category?.code ?? '', disabled: this.editing},
      [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]*$/), Validators.maxLength(80)],
    ],
    name: [this.data.category?.name ?? '', [Validators.required, Validators.maxLength(100)]],
    description: [this.data.category?.description ?? '', [Validators.maxLength(255)]],
    mandatory: [this.data.category?.mandatory ?? false],
  });

  apply(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.ref.close({
      code: value.code.trim(),
      name: value.name.trim(),
      description: value.description.trim() || null,
      mandatory: value.mandatory,
    });
  }
}
