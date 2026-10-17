import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DocumentWorkspace } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { DocumentWorkspaceService } from '../data/document-workspace.service';

export interface DuplicateWorkspaceData {
  workspace: DocumentWorkspace;
}

/** Copies a workspace (content and configuration, never people or history) under a new name. */
@Component({
  selector: 'app-duplicate-workspace-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent, TextFieldComponent, TranslatePipe],
  template: `
    <form class="wd" (submit)="$event.preventDefault(); submit()">
      <div class="wd__header">
        <h2 class="wd__title">{{ 'workspaces.duplicate.title' | t }}</h2>
        <button type="button" class="wd__close" (click)="ref.close()" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>
      <div class="wd__body">
        <app-text-field [label]="'workspaces.duplicate.name' | t" [formControl]="name" [required]="true" />
        <div class="wd__note">
          <app-icon name="info" [size]="16" />
          <span>{{ 'workspaces.duplicate.note' | t }}</span>
        </div>
      </div>
      <div class="wd__footer">
        <app-button variant="ghost" (click)="ref.close()">{{ 'common.cancel' | t }}</app-button>
        <app-button variant="primary" type="submit" [loading]="busy()" [disabled]="name.invalid || busy()">
          {{ 'workspaces.duplicate.confirm' | t }}
        </app-button>
      </div>
    </form>
  `,
  styleUrl: './dialog.scss',
})
export class DuplicateWorkspaceDialogComponent {
  readonly ref = inject<DialogRef<DocumentWorkspace | undefined>>(DialogRef);
  private readonly data = inject<DuplicateWorkspaceData>(DIALOG_DATA);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly busy = signal(false);
  readonly name = new FormControl(
    this.i18n.translate('workspaces.duplicate.defaultName', { name: this.data.workspace.name }).slice(0, 120),
    { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] },
  );

  submit(): void {
    if (this.name.invalid || this.busy()) return;
    this.busy.set(true);
    this.workspaces.duplicate(this.data.workspace.id, this.name.value.trim()).subscribe({
      next: (copy) => this.ref.close(copy),
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
