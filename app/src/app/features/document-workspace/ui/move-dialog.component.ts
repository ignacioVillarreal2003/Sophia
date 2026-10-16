import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { Artifact } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { ArtifactService } from '../data/artifact.service';

export interface MoveDialogData {
  documentWorkspaceId: string;
  artifact: Artifact;
}

/** `null` is the workspace root. */
export interface MoveDialogResult {
  parentId: string | null;
}

interface Step {
  id: string | null;
  name: string;
}

/** Folder picker: browse folders level by level and pick where an item should go. */
@Component({
  selector: 'app-move-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SpinnerComponent, TranslatePipe],
  template: `
    <div class="mv">
      <h2 class="mv__title">{{ 'explorer.move.title' | t: { name: data.artifact.name } }}</h2>

      <nav class="mv__trail" [attr.aria-label]="'a11y.breadcrumbNav' | t">
        @for (step of trail(); track $index; let last = $last) {
          <button type="button" class="mv__crumb" [disabled]="last" (click)="goTo($index)">{{ step.name }}</button>
          @if (!last) {
            <app-icon name="chevron-right" [size]="14" class="mv__sep" />
          }
        }
      </nav>

      <div class="mv__list">
        @if (loading()) {
          <div class="mv__state"><app-spinner [size]="20" /></div>
        } @else if (folders().length === 0) {
          <div class="mv__state">{{ 'explorer.move.empty' | t }}</div>
        } @else {
          @for (folder of folders(); track folder.id) {
            <button type="button" class="mv__folder" (click)="enter(folder)">
              <app-icon name="folder" [size]="18" />
              <span class="mv__folder-name">{{ folder.name }}</span>
              <app-icon name="chevron-right" [size]="16" class="mv__chevron" />
            </button>
          }
        }
      </div>

      <div class="mv__actions">
        <app-button variant="ghost" type="button" (click)="ref.close()">{{ 'common.cancel' | t }}</app-button>
        <app-button variant="primary" type="button" [disabled]="isCurrentLocation()" (click)="confirm()">
          {{ 'explorer.move.here' | t }}
        </app-button>
      </div>
    </div>
  `,
  styles: [
    `
      .mv {
        width: min(92vw, 480px);
        padding: var(--space-lg);
      }
      .mv__title {
        margin-bottom: var(--space-md);
        font-size: var(--type-tagline-size);
        font-weight: 600;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .mv__trail {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 2px;
        margin-bottom: var(--space-sm);
      }
      .mv__crumb {
        padding: 3px 8px;
        border-radius: var(--radius-sm);
        font-size: var(--type-caption-size);
        color: var(--text-muted);
        cursor: pointer;
        max-width: 180px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        &:hover:not(:disabled) {
          background: var(--surface-sunken);
          color: var(--text);
        }
        &:disabled {
          color: var(--text);
          font-weight: 600;
          cursor: default;
        }
      }
      .mv__sep {
        color: var(--text-subtle);
      }
      .mv__list {
        height: 260px;
        overflow-y: auto;
        border-radius: var(--radius-md);
        background: var(--surface);
        box-shadow: inset 0 0 0 1px var(--border);
      }
      .mv__state {
        height: 100%;
        display: grid;
        place-items: center;
        font-size: var(--type-caption-size);
        color: var(--text-subtle);
      }
      .mv__folder {
        display: flex;
        align-items: center;
        gap: 12px;
        width: 100%;
        padding: 12px 16px;
        text-align: left;
        color: var(--text);
        cursor: pointer;
        &:not(:last-child) {
          border-bottom: 1px solid var(--divider);
        }
        &:hover {
          background: var(--surface-muted);
        }
      }
      .mv__folder-name {
        flex: 1;
        min-width: 0;
        font-weight: 600;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .mv__chevron {
        color: var(--text-subtle);
      }
      .mv__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class MoveDialogComponent implements OnInit {
  readonly ref = inject<DialogRef<MoveDialogResult>>(DialogRef);
  readonly data = inject<MoveDialogData>(DIALOG_DATA);
  private readonly artifacts = inject(ArtifactService);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly i18n = inject(TranslationService);

  readonly trail = signal<Step[]>([]);
  readonly folders = signal<Artifact[]>([]);
  readonly loading = signal(true);

  readonly current = computed(() => this.trail().at(-1)?.id ?? null);
  /** Moving an item to the folder it already sits in is a no-op. */
  readonly isCurrentLocation = computed(() => this.current() === (this.data.artifact.parentId ?? null));

  ngOnInit(): void {
    const rootName = this.workspaces.active()?.name ?? this.i18n.translate('documents.home');
    this.trail.set([{ id: null, name: rootName }]);
    this.load();
  }

  enter(folder: Artifact): void {
    this.trail.update((t) => [...t, { id: folder.id, name: folder.name }]);
    this.load();
  }

  goTo(index: number): void {
    this.trail.update((t) => t.slice(0, index + 1));
    this.load();
  }

  confirm(): void {
    this.ref.close({ parentId: this.current() });
  }

  private load(): void {
    this.loading.set(true);
    this.artifacts.list(this.data.documentWorkspaceId, this.current()).subscribe({
      next: (page) => {
        // The item itself (and so everything below it) is not a valid destination.
        this.folders.set(page.content.filter((a) => a.type === 'FOLDER' && a.id !== this.data.artifact.id));
        this.loading.set(false);
      },
      error: () => {
        this.folders.set([]);
        this.loading.set(false);
      },
    });
  }
}
