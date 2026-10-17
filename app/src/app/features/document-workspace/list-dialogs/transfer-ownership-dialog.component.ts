import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DocumentWorkspace, DocumentWorkspaceMember } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CheckboxComponent } from '@shared/ui/checkbox/checkbox.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { SelectFieldComponent, SelectOption } from '@shared/ui/form-field/select-field.component';
import { DocumentWorkspaceService } from '../data/document-workspace.service';

export interface TransferOwnershipData {
  workspace: DocumentWorkspace;
}

/** The owner hands the workspace to another active member, who becomes its owner (and stays admin). */
@Component({
  selector: 'app-transfer-ownership-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, CheckboxComponent, IconComponent, SpinnerComponent, SelectFieldComponent, TranslatePipe],
  template: `
    <form class="wd" (submit)="$event.preventDefault(); submit()">
      <div class="wd__header">
        <h2 class="wd__title">{{ 'workspaces.transfer.title' | t }}</h2>
        <button type="button" class="wd__close" (click)="ref.close(false)" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>
      <div class="wd__body">
        @if (loading()) {
          <div class="wd__loading"><app-spinner /></div>
        } @else if (options().length === 0) {
          <div class="wd__note">
            <app-icon name="info" [size]="16" />
            <span>{{ 'workspaces.transfer.noMembers' | t }}</span>
          </div>
        } @else {
          <p class="wd__text">{{ 'workspaces.transfer.intro' | t: { name: data.workspace.name } }}</p>
          <app-select-field [label]="'workspaces.transfer.newOwner' | t" [options]="options()" [formControl]="target" />
          <div class="wd__note wd__note--danger">
            <app-icon name="alert-circle" [size]="16" />
            <span>{{ 'workspaces.transfer.warning' | t }}</span>
          </div>
          <app-checkbox [formControl]="understood" [label]="'workspaces.transfer.understood' | t" />
        }
      </div>
      <div class="wd__footer">
        <app-button variant="ghost" (click)="ref.close(false)">{{ 'common.cancel' | t }}</app-button>
        <app-button variant="danger" type="submit" [loading]="busy()" [disabled]="!ready() || busy()">
          {{ 'workspaces.transfer.confirm' | t }}
        </app-button>
      </div>
    </form>
  `,
  styleUrl: './dialog.scss',
})
export class TransferOwnershipDialogComponent implements OnInit {
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly data = inject<TransferOwnershipData>(DIALOG_DATA);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly busy = signal(false);
  private readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly target = new FormControl('', { nonNullable: true });
  readonly understood = new FormControl(false, { nonNullable: true });
  private readonly targetValue = signal('');
  private readonly understoodValue = signal(false);

  readonly options = computed<SelectOption[]>(() =>
    this.members()
      .filter((m) => m.status === 'ACTIVE' && m.userId !== this.data.workspace.ownerId)
      .map((m) => ({ value: m.userId, label: m.fullName ? `${m.fullName} · ${m.email}` : m.email })),
  );
  readonly ready = computed(() => !!this.targetValue() && this.understoodValue());

  ngOnInit(): void {
    this.target.valueChanges.subscribe((v) => this.targetValue.set(v));
    this.understood.valueChanges.subscribe((v) => this.understoodValue.set(v));
    this.workspaces.members(this.data.workspace.id).subscribe({
      next: (members) => {
        this.members.set(members);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submit(): void {
    if (!this.ready() || this.busy()) return;
    this.busy.set(true);
    this.workspaces.transferOwnership(this.data.workspace.id, this.target.value).subscribe({
      next: () => this.ref.close(true),
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
