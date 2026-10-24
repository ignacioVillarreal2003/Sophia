import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ArtifactNode } from '@core/models/document-workspace';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { artifactTone } from '@shared/utils/artifact-tone';
import { ArtifactTreeStore } from '../data/artifact-tree.store';

/**
 * Recursive artifact tree row. Folders expand/collapse lazily; leaves link to
 * the document view. Rendered inside the workspace sidebar.
 */
@Component({
  selector: 'app-artifact-tree',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, IconComponent, TranslatePipe],
  template: `
    @for (node of nodes(); track node.id) {
      <div class="tree__node">
        @if (isFolder(node)) {
          <div
            class="tree__row"
            [style.padding-left.px]="8 + depth() * 14"
            (click)="store.toggle(node)"
          >
            <app-icon
              class="tree__caret"
              [class.tree__caret--open]="node.expanded"
              name="chevron-right"
              [size]="14"
            />
            <app-icon class="tree__icon" [style.color]="tone(node)" [name]="node.expanded ? 'folder-open' : 'folder'" [size]="16" />
            <span class="tree__name">{{ node.name }}</span>
            <button
              class="tree__add"
              (click)="$event.stopPropagation(); create.emit(node)"
              [attr.aria-label]="'artifactTree.addAria' | t"
            >
              <app-icon name="plus" [size]="13" />
            </button>
          </div>
          @if (node.expanded) {
            @if (node.loading) {
              <div class="tree__loading" [style.padding-left.px]="30 + depth() * 14">…</div>
            } @else {
              <app-artifact-tree
                [nodes]="node.children ?? []"
                [depth]="depth() + 1"
                [documentWorkspaceId]="documentWorkspaceId()"
                (create)="create.emit($event)"
              />
            }
          }
        } @else {
          <a
            class="tree__row tree__row--leaf"
            [style.padding-left.px]="24 + depth() * 14"
            [routerLink]="['/document-workspaces', documentWorkspaceId(), 'a', node.id]"
            routerLinkActive="tree__row--active"
          >
            <app-icon class="tree__icon" [style.color]="tone(node)" [name]="iconFor(node)" [size]="16" />
            <span class="tree__name">{{ node.name }}</span>
          </a>
        }
      </div>
    }
  `,
  styleUrl: './artifact-tree.component.scss',
})
export class ArtifactTreeComponent {
  readonly store = inject(ArtifactTreeStore);

  readonly nodes = input.required<ArtifactNode[]>();
  readonly depth = input<number>(0);
  readonly documentWorkspaceId = input.required<string>();
  readonly create = output<ArtifactNode>();

  isFolder(node: ArtifactNode): boolean {
    return node.type === 'FOLDER';
  }

  tone(node: ArtifactNode): string {
    return artifactTone(node.type);
  }

  iconFor(node: ArtifactNode) {
    return artifactIcon(node.type);
  }
}
