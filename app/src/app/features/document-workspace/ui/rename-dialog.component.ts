import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { AutofocusDirective } from '@shared/directives/autofocus.directive';

export interface RenameDialogData {
  name: string;
}

/** Small "Rename" modal; closes with the new name, or nothing when cancelled. */
@Component({
  selector: 'app-rename-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, TextFieldComponent, AutofocusDirective, TranslatePipe],
  template: `
    <form class="rn" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h2 class="rn__title">{{ 'explorer.rename.title' | t }}</h2>
      <app-text-field appAutofocus formControlName="name" required />
      <div class="rn__actions">
        <app-button variant="ghost" type="button" (click)="ref.close()">{{ 'common.cancel' | t }}</app-button>
        <app-button variant="primary" type="submit" [disabled]="form.invalid">{{ 'common.save' | t }}</app-button>
      </div>
    </form>
  `,
  styles: [
    `
      .rn {
        padding: var(--space-lg);
        width: min(90vw, 420px);
      }
      .rn__title {
        margin-bottom: var(--space-md);
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }
      .rn__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class RenameDialogComponent {
  private readonly fb = inject(FormBuilder);
  readonly ref = inject<DialogRef<string>>(DialogRef);
  private readonly data = inject<RenameDialogData>(DIALOG_DATA);

  readonly form = this.fb.nonNullable.group({
    name: [this.data.name, [Validators.required]],
  });

  submit(): void {
    const name = this.form.getRawValue().name.trim();
    if (this.form.invalid || !name) return;
    this.ref.close(name);
  }
}
