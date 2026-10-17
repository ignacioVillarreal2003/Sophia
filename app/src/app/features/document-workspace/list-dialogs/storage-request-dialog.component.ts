import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DocumentWorkspace } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { FileSizePipe } from '@shared/pipes/file-size.pipe';
import { WorkspaceRequestsService } from '../data/workspace-extras.service';

export interface StorageRequestData {
  workspace: DocumentWorkspace;
}

const PRESETS_MB = [500, 1024, 5120, 10240];

/** The owner of a workspace that is running out of space asks the platform administrators for more. */
@Component({
  selector: 'app-storage-request-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent, TextFieldComponent, TextareaComponent, TranslatePipe, FileSizePipe],
  template: `
    <form class="wd" (submit)="$event.preventDefault(); submit()">
      <div class="wd__header">
        <h2 class="wd__title">{{ 'workspaces.storage.title' | t }}</h2>
        <button type="button" class="wd__close" (click)="ref.close(false)" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>
      <div class="wd__body">
        @if (data.workspace.stats; as stats) {
          <p class="wd__text">
            {{ 'workspaces.storage.usage' | t: { used: (stats.usedBytes | fileSize), quota: quotaLabel(stats.quotaMb) } }}
          </p>
        }
        <div class="wd__chips">
          @for (preset of presets; track preset) {
            <button type="button" class="wd__chip" [class.wd__chip--active]="amount.value === '' + preset" (click)="amount.setValue('' + preset)">
              +{{ preset >= 1024 ? preset / 1024 + ' GB' : preset + ' MB' }}
            </button>
          }
        </div>
        <app-text-field type="number" [label]="'workspaces.storage.amount' | t" [formControl]="amount" [required]="true" />
        <app-textarea [label]="'workspaces.storage.message' | t" [rows]="3" [formControl]="message" />
      </div>
      <div class="wd__footer">
        <app-button variant="ghost" (click)="ref.close(false)">{{ 'common.cancel' | t }}</app-button>
        <app-button variant="primary" type="submit" [loading]="busy()" [disabled]="amount.invalid || busy()">
          {{ 'workspaces.storage.send' | t }}
        </app-button>
      </div>
    </form>
  `,
  styleUrl: './dialog.scss',
})
export class StorageRequestDialogComponent {
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly data = inject<StorageRequestData>(DIALOG_DATA);
  private readonly requests = inject(WorkspaceRequestsService);
  private readonly toast = inject(ToastService);

  readonly presets = PRESETS_MB;
  readonly busy = signal(false);
  readonly amount = new FormControl('1024', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^[1-9][0-9]{0,6}$/)],
  });
  readonly message = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] });

  quotaLabel(quotaMb: number | null): string {
    if (quotaMb == null) return '∞';
    return quotaMb >= 1024 ? `${+(quotaMb / 1024).toFixed(1)} GB` : `${quotaMb} MB`;
  }

  submit(): void {
    if (this.amount.invalid || this.busy()) return;
    this.busy.set(true);
    this.requests.requestStorage(this.data.workspace.id, Number(this.amount.value), this.message.value.trim()).subscribe({
      next: () => this.ref.close(true),
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
