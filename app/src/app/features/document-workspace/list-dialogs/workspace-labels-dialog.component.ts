import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DocumentWorkspace, WorkspaceLabel } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { WorkspaceLabelService } from '../data/workspace-extras.service';

export interface WorkspaceLabelsData {
  /** When given, the dialog assigns labels to this workspace; otherwise it only manages the label catalog. */
  workspace?: DocumentWorkspace;
}

/** The palette offered for labels; any #RRGGBB the backend accepts would do. */
export const LABEL_COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#14b8a6', '#3b82f6', '#8b5cf6', '#ec4899', '#64748b'];

const MAX_PER_WORKSPACE = 10;

/**
 * Personal labels: create, rename, recolor and delete them, and (when opened for a workspace) pick which ones it
 * carries. Labels belong to the user; nobody else sees them.
 */
@Component({
  selector: 'app-workspace-labels-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent, TextFieldComponent, TranslatePipe],
  template: `
    <div class="wd wl">
      <div class="wd__header">
        <h2 class="wd__title">
          {{ (data.workspace ? 'workspaces.labels.assignTitle' : 'workspaces.labels.manageTitle') | t }}
        </h2>
        <button type="button" class="wd__close" (click)="cancel()" [attr.aria-label]="'common.close' | t">
          <app-icon name="x" [size]="18" />
        </button>
      </div>

      <div class="wd__body">
        @if (data.workspace) {
          <p class="wd__text">{{ 'workspaces.labels.assignIntro' | t: { name: data.workspace.name } }}</p>
        }

        @if (labels().length === 0) {
          <p class="wd__text">{{ 'workspaces.labels.none' | t }}</p>
        }

        <ul class="wl__list">
          @for (label of labels(); track label.id) {
            <li class="wl__item">
              @if (editingId() === label.id) {
                <div class="wl__edit">
                  <app-text-field [formControl]="editName" [label]="'workspaces.labels.name' | t" />
                  <div class="wl__swatches">
                    @for (color of colors; track color) {
                      <button
                        type="button"
                        class="wl__swatch"
                        [class.wl__swatch--on]="editColor() === color"
                        [style.background]="color"
                        [attr.aria-label]="color"
                        (click)="editColor.set(color)"
                      ></button>
                    }
                  </div>
                  <div class="wl__edit-actions">
                    <app-button size="sm" variant="ghost" (click)="editingId.set(null)">{{ 'common.cancel' | t }}</app-button>
                    <app-button size="sm" variant="primary" [disabled]="editName.invalid || busy()" (click)="saveEdit(label)">
                      {{ 'common.save' | t }}
                    </app-button>
                  </div>
                </div>
              } @else {
                @if (data.workspace) {
                  <label class="wl__pick">
                    <input
                      type="checkbox"
                      [checked]="selected().has(label.id)"
                      [disabled]="!selected().has(label.id) && selected().size >= max"
                      (change)="toggle(label.id)"
                    />
                    <span class="wl__dot" [style.background]="label.color"></span>
                    <span class="wl__name">{{ label.name }}</span>
                  </label>
                } @else {
                  <span class="wl__pick">
                    <span class="wl__dot" [style.background]="label.color"></span>
                    <span class="wl__name">{{ label.name }}</span>
                  </span>
                }
                <button type="button" class="wl__icon" [attr.aria-label]="'common.edit' | t" (click)="startEdit(label)">
                  <app-icon name="edit" [size]="15" />
                </button>
                <button type="button" class="wl__icon wl__icon--danger" [attr.aria-label]="'common.delete' | t" (click)="remove(label)">
                  <app-icon name="trash" [size]="15" />
                </button>
              }
            </li>
          }
        </ul>

        <form class="wl__new" (submit)="$event.preventDefault(); create()">
          <app-text-field [formControl]="newName" [label]="'workspaces.labels.new' | t" [placeholder]="'workspaces.labels.newPlaceholder' | t" />
          <div class="wl__swatches">
            @for (color of colors; track color) {
              <button
                type="button"
                class="wl__swatch"
                [class.wl__swatch--on]="newColor() === color"
                [style.background]="color"
                [attr.aria-label]="color"
                (click)="newColor.set(color)"
              ></button>
            }
          </div>
          <app-button type="submit" variant="secondary" size="sm" icon="plus" [disabled]="newName.invalid || busy()">
            {{ 'workspaces.labels.add' | t }}
          </app-button>
        </form>
      </div>

      @if (data.workspace) {
        <div class="wd__footer">
          <app-button variant="ghost" (click)="cancel()">{{ 'common.cancel' | t }}</app-button>
          <app-button variant="primary" [loading]="busy()" [disabled]="busy()" (click)="apply()">
            {{ 'common.save' | t }}
          </app-button>
        </div>
      } @else {
        <div class="wd__footer">
          <app-button variant="primary" (click)="ref.close(true)">{{ 'common.done' | t }}</app-button>
        </div>
      }
    </div>
  `,
  styleUrl: './dialog.scss',
  styles: [
    `
      .wl__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
      .wl__item { display: flex; align-items: center; gap: 6px; padding: 4px 6px; border-radius: var(--radius-sm); }
      .wl__item:hover { background: var(--surface-sunken); }
      .wl__pick { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; cursor: pointer; }
      .wl__dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
      .wl__name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--type-body-size); }
      .wl__icon { display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: var(--radius-sm); background: none; color: var(--text-subtle); cursor: pointer; }
      .wl__icon:hover { background: var(--surface-raised); color: var(--text); }
      .wl__icon--danger:hover { color: #d92d20; }
      .wl__edit { display: flex; flex-direction: column; gap: 8px; width: 100%; padding: 6px 0; }
      .wl__edit-actions { display: flex; justify-content: flex-end; gap: 6px; }
      .wl__new { display: flex; flex-direction: column; gap: 10px; padding-top: var(--space-sm); border-top: 1px solid var(--divider); }
      .wl__swatches { display: flex; flex-wrap: wrap; gap: 8px; }
      .wl__swatch { width: 22px; height: 22px; border-radius: 50%; border: 2px solid transparent; cursor: pointer; }
      .wl__swatch--on { box-shadow: 0 0 0 2px var(--surface-raised), 0 0 0 4px var(--text); }
    `,
  ],
})
export class WorkspaceLabelsDialogComponent implements OnInit {
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly data = inject<WorkspaceLabelsData>(DIALOG_DATA);
  private readonly service = inject(WorkspaceLabelService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly colors = LABEL_COLORS;
  readonly max = MAX_PER_WORKSPACE;
  readonly labels = this.service.labels;
  readonly busy = signal(false);
  readonly selected = signal<Set<string>>(new Set((this.data.workspace?.labels ?? []).map((l) => l.id)));

  readonly newName = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(40)] });
  readonly newColor = signal(LABEL_COLORS[5]);
  readonly editingId = signal<string | null>(null);
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(40)] });
  readonly editColor = signal(LABEL_COLORS[0]);

  /** Whether anything changed in the catalog, so the caller can refresh the list behind. */
  private changed = false;

  ngOnInit(): void {
    this.service.load().subscribe();
  }

  toggle(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  create(): void {
    if (this.newName.invalid || this.busy()) return;
    this.busy.set(true);
    this.service.create(this.newName.value.trim(), this.newColor()).subscribe({
      next: (label) => {
        this.busy.set(false);
        this.changed = true;
        this.newName.reset('');
        if (this.data.workspace && this.selected().size < MAX_PER_WORKSPACE) {
          this.toggle(label.id);
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }

  startEdit(label: WorkspaceLabel): void {
    this.editingId.set(label.id);
    this.editName.setValue(label.name);
    this.editColor.set(label.color);
  }

  saveEdit(label: WorkspaceLabel): void {
    if (this.editName.invalid || this.busy()) return;
    this.busy.set(true);
    this.service.update(label.id, { name: this.editName.value.trim(), color: this.editColor() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.changed = true;
        this.editingId.set(null);
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }

  remove(label: WorkspaceLabel): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.service.remove(label.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.changed = true;
        this.selected.update((set) => {
          const next = new Set(set);
          next.delete(label.id);
          return next;
        });
        this.toast.success(this.i18n.translate('workspaces.labels.deleted', { name: label.name }));
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }

  cancel(): void {
    this.ref.close(this.changed);
  }

  apply(): void {
    const workspace = this.data.workspace;
    if (!workspace || this.busy()) return;
    this.busy.set(true);
    this.service.assign(workspace.id, [...this.selected()]).subscribe({
      next: () => this.ref.close(true),
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
