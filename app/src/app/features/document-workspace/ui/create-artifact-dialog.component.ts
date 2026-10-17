import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ArtifactType, CreateArtifactRequest } from '@core/models/document-workspace';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { AutofocusDirective } from '@shared/directives/autofocus.directive';

export interface CreateArtifactData {
  parentId: string | null;
  /** Type to preselect (e.g. when launched from "New folder"). */
  type?: ArtifactType;
}

interface TypeOption {
  type: ArtifactType;
  label: string;
  icon: IconName;
}

@Component({
  selector: 'app-create-artifact-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    TextFieldComponent,
    IconComponent,
    AutofocusDirective,
    TranslatePipe,
  ],
  template: `
    <div class="ca">
      <h2 class="ca__title">{{ 'createArtifact.title' | t }}</h2>

      <div class="ca__types">
        @for (opt of options; track opt.type) {
          <button
            type="button"
            class="ca__type"
            [class.ca__type--active]="type() === opt.type"
            (click)="type.set(opt.type)"
          >
            <app-icon [name]="opt.icon" [size]="20" />
            <span>{{ opt.label | t }}</span>
          </button>
        }
      </div>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <app-text-field
          appAutofocus
          formControlName="name"
          [placeholder]="'createArtifact.namePlaceholder' | t"
          required
        />
        <div class="ca__actions">
          <app-button variant="ghost" type="button" (click)="ref.close()">{{ 'createArtifact.cancel' | t }}</app-button>
          <app-button variant="primary" type="submit" [disabled]="form.invalid">{{ 'createArtifact.create' | t }}</app-button>
        </div>
      </form>
    </div>
  `,
  styleUrl: './create-artifact-dialog.component.scss',
})
export class CreateArtifactDialogComponent {
  private readonly fb = inject(FormBuilder);
  readonly ref = inject<DialogRef<CreateArtifactRequest>>(DialogRef);
  readonly data = inject<CreateArtifactData>(DIALOG_DATA);

  readonly options: TypeOption[] = [
    { type: 'MARKDOWN_DOCUMENT', label: 'createArtifact.type.MARKDOWN_DOCUMENT', icon: 'file-text' },
    { type: 'FOLDER', label: 'createArtifact.type.FOLDER', icon: 'folder' },
    { type: 'CHECKLIST', label: 'createArtifact.type.CHECKLIST', icon: 'check-circle' },
    { type: 'SPREADSHEET', label: 'createArtifact.type.SPREADSHEET', icon: 'table' },
    { type: 'PRESENTATION', label: 'createArtifact.type.PRESENTATION', icon: 'layout-cards' },
    { type: 'WHITEBOARD', label: 'createArtifact.type.WHITEBOARD', icon: 'pencil' },
  ];

  readonly type = signal<ArtifactType>(this.data.type ?? 'MARKDOWN_DOCUMENT');

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.ref.close({
      parentId: this.data.parentId,
      name: this.form.getRawValue().name,
      type: this.type(),
    });
  }
}
