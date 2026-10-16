import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AppError } from '@core/models/api.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { firstErrorKey } from '@shared/utils/form-errors';
import { WORKSPACE_COLORS } from '../workspace-colors';

const SWATCHES = WORKSPACE_COLORS;

@Component({
  selector: 'app-create-document-workspace',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    TextFieldComponent,
    TextareaComponent,
    IconComponent,
    PageHeaderComponent,
    TranslatePipe,
  ],
  templateUrl: './create-document-workspace.component.html',
  styleUrl: './create-document-workspace.component.scss',
})
export class CreateDocumentWorkspaceComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DocumentWorkspaceService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);

  readonly swatches = SWATCHES;
  readonly color = signal(SWATCHES[0]);
  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
  });

  /** Live values for the preview tile. */
  readonly draft = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    return key ? this.i18n.translate(key) : null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    const { name, description } = this.form.getRawValue();

    this.service
      .create({ name, description, color: this.color(), language: this.i18n.locale() })
      .subscribe({
        next: (ws) => {
          this.toast.success(this.i18n.translate('common.create'));
          this.service.setActive(ws.id);
          this.router.navigate(['/document-workspaces', ws.id]);
        },
        error: (err: AppError) => {
          this.loading.set(false);
          this.toast.error(err.message);
        },
      });
  }
}
