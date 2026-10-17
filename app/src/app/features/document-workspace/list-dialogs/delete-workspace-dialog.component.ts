import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DocumentWorkspace, WorkspaceImpact } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { FileSizePipe } from '@shared/pipes/file-size.pipe';
import { DocumentWorkspaceService } from '../data/document-workspace.service';

export interface DeleteWorkspaceData {
  workspace: DocumentWorkspace;
}

/**
 * Sending a workspace to the trash: shows what goes with it and asks for its name, so a stray click cannot do it.
 * The workspace stays recoverable from the trash for a while.
 */
@Component({
  selector: 'app-delete-workspace-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent, SpinnerComponent, TextFieldComponent, TranslatePipe, FileSizePipe],
  template: `
    <form class="wd" (submit)="$event.preventDefault(); submit()">
      <div class="wd__header">
        <h2 class="wd__title">{{ 'workspaces.delete.title' | t }}</h2>
        <button type="button" class="wd__close" (click)="ref.close(false)" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>
      <div class="wd__body">
        <p class="wd__text">{{ 'workspaces.delete.intro' | t: { name: data.workspace.name } }}</p>

        @if (impact(); as i) {
          <ul class="wd__stats">
            <li class="wd__stat">
              <span class="wd__stat-value">{{ i.documents }}</span>
              <span class="wd__stat-label">{{ 'workspaces.delete.items' | t: { folders: i.folders } }}</span>
            </li>
            <li class="wd__stat">
              <span class="wd__stat-value">{{ i.files }}</span>
              <span class="wd__stat-label">{{ 'workspaces.delete.files' | t }} · {{ i.usedBytes | fileSize }}</span>
            </li>
            <li class="wd__stat">
              <span class="wd__stat-value">{{ i.members }}</span>
              <span class="wd__stat-label">{{ 'workspaces.delete.members' | t }}</span>
            </li>
            <li class="wd__stat">
              <span class="wd__stat-value">{{ i.activePublicLinks }}</span>
              <span class="wd__stat-label">{{ 'workspaces.delete.publicLinks' | t }}</span>
            </li>
          </ul>
        } @else if (loading()) {
          <div class="wd__loading"><app-spinner /></div>
        }

        <div class="wd__note wd__note--danger">
          <app-icon name="alert-circle" [size]="16" />
          <span>{{ 'workspaces.delete.recoverable' | t: { days: retentionDays } }}</span>
        </div>

        <app-text-field
          [label]="'workspaces.delete.typeName' | t: { name: data.workspace.name }"
          [formControl]="confirmation"
          [placeholder]="data.workspace.name"
        />
      </div>
      <div class="wd__footer">
        <app-button variant="ghost" (click)="ref.close(false)">{{ 'common.cancel' | t }}</app-button>
        <app-button variant="danger" type="submit" [loading]="busy()" [disabled]="!matches() || busy()">
          {{ 'workspaces.delete.confirm' | t }}
        </app-button>
      </div>
    </form>
  `,
  styleUrl: './dialog.scss',
})
export class DeleteWorkspaceDialogComponent implements OnInit {
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly data = inject<DeleteWorkspaceData>(DIALOG_DATA);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly toast = inject(ToastService);

  readonly impact = signal<WorkspaceImpact | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly confirmation = new FormControl('', { nonNullable: true });
  private readonly typed = signal('');
  readonly matches = computed(() => this.typed().trim() === this.data.workspace.name.trim());
  /** Days a deleted workspace waits in the trash (backend default). */
  readonly retentionDays = 30;

  ngOnInit(): void {
    this.confirmation.valueChanges.subscribe((value) => this.typed.set(value));
    this.workspaces.impact(this.data.workspace.id).subscribe({
      next: (impact) => {
        this.impact.set(impact);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submit(): void {
    if (!this.matches() || this.busy()) return;
    this.busy.set(true);
    this.workspaces.remove(this.data.workspace.id).subscribe({
      next: () => this.ref.close(true),
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
