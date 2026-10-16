import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Artifact } from '@core/models/document-workspace';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { artifactIcon } from '@shared/utils/artifact-icon';

/** Compact clickable card representing an artifact in a grid/list. */
@Component({
  selector: 'app-artifact-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RelativeTimePipe],
  template: `
    <button class="ac" (click)="open.emit(artifact())" type="button">
      <span class="ac__icon"><app-icon [name]="icon()" [size]="20" /></span>
      <span class="ac__body">
        <span class="ac__name">{{ artifact().name }}</span>
        <span class="ac__meta">{{ artifact().updatedAt | relativeTime }}</span>
      </span>
    </button>
  `,
  styles: [
    `
      .ac {
        display: flex;
        align-items: center;
        gap: var(--space-sm);
        width: 100%;
        padding: 14px 16px;
        text-align: left;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
        transition: border-color var(--duration-fast) var(--ease-standard),
          transform var(--duration-fast) var(--ease-standard);
        &:hover { border-color: var(--border-strong); }
        &:active { transform: scale(0.99); }
      }
      .ac__icon {
        width: 38px;
        height: 38px;
        flex-shrink: 0;
        display: grid;
        place-items: center;
        border-radius: var(--radius-sm);
        background: var(--surface-sunken);
        color: var(--accent);
      }
      .ac__body {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .ac__name {
        font-size: var(--type-body-strong-size);
        font-weight: 600;
        letter-spacing: var(--type-body-ls);
        color: var(--text);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .ac__meta {
        font-size: 12px;
        color: var(--text-subtle);
      }
    `,
  ],
})
export class ArtifactCardComponent {
  readonly artifact = input.required<Artifact>();
  readonly open = output<Artifact>();

  readonly icon = computed(() => artifactIcon(this.artifact().type));
}
