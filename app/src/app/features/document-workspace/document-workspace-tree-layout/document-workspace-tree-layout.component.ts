import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { ArtifactTreeStore } from '../data/artifact-tree.store';

/**
 * Workspace-scoped route host. Its navigation lives in the shell's sidebar
 * (name header, Home/Members/Settings, back); this only provides the
 * ArtifactTreeStore shared by the explorer and the editor and marks the
 * workspace as active.
 */
@Component({
  selector: 'app-document-workspace-tree-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ArtifactTreeStore],
  imports: [RouterOutlet, IconComponent, TranslatePipe],
  templateUrl: './document-workspace-tree-layout.component.html',
  styleUrl: './document-workspace-tree-layout.component.scss',
})
export class DocumentWorkspaceTreeLayoutComponent implements OnInit {
  private readonly store = inject(ArtifactTreeStore);
  readonly documentWorkspaces = inject(DocumentWorkspaceService);

  readonly documentWorkspaceId = input.required<string>();

  ngOnInit(): void {
    this.store.init(this.documentWorkspaceId());
    this.documentWorkspaces.setActive(this.documentWorkspaceId());
    // The cached list is only the first page, so fetch this workspace directly for its name.
    this.documentWorkspaces.get(this.documentWorkspaceId()).subscribe();
  }
}
