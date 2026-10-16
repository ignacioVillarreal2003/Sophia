import { Injectable, inject, signal } from '@angular/core';
import { ArtifactNode, ArtifactType, CreateArtifactRequest } from '@core/models/document-workspace';
import { Observable, tap } from 'rxjs';
import { ArtifactService } from './artifact.service';

/**
 * Reactive artifact tree for the workspace sidebar. Holds root + lazily-loaded
 * children keyed by parent id, exposed as a signal so the tree re-renders on
 * expand/create/move without manual change detection.
 */
@Injectable()
export class ArtifactTreeStore {
  private readonly artifacts = inject(ArtifactService);

  private documentWorkspaceId = '';
  private readonly _roots = signal<ArtifactNode[]>([]);
  private readonly _loading = signal(true);
  private readonly childrenCache = new Map<string, ArtifactNode[]>();

  readonly roots = this._roots.asReadonly();
  readonly loading = this._loading.asReadonly();

  init(documentWorkspaceId: string): void {
    if (this.documentWorkspaceId === documentWorkspaceId && this._roots().length) {
      return;
    }
    this.documentWorkspaceId = documentWorkspaceId;
    this.childrenCache.clear();
    this.loadRoots();
  }

  loadRoots(): void {
    this._loading.set(true);
    this.artifacts.list(this.documentWorkspaceId, null).subscribe({
      next: (page) => {
        this._roots.set(page.content.map((a) => ({ ...a })));
        this._loading.set(false);
      },
      error: () => this._loading.set(false),
    });
  }

  toggle(node: ArtifactNode): void {
    if (node.type !== 'FOLDER') return;
    node.expanded = !node.expanded;
    if (node.expanded && !node.children) {
      this.loadChildren(node);
    }
    this._roots.set([...this._roots()]);
  }

  private loadChildren(node: ArtifactNode): void {
    node.loading = true;
    this.artifacts.list(this.documentWorkspaceId, node.id).subscribe({
      next: (page) => {
        node.children = page.content.map((a) => ({ ...a }));
        node.loading = false;
        this.childrenCache.set(node.id, node.children);
        this._roots.set([...this._roots()]);
      },
      error: () => {
        node.loading = false;
        node.children = [];
        this._roots.set([...this._roots()]);
      },
    });
  }

  create(request: CreateArtifactRequest): Observable<ArtifactNode> {
    return this.artifacts.create(this.documentWorkspaceId, request).pipe(
      tap((created) => this.insert(created as ArtifactNode, request.parentId ?? null)),
    ) as Observable<ArtifactNode>;
  }

  remove(id: string, parentId: string | null): Observable<void> {
    return this.artifacts.trash(this.documentWorkspaceId, id).pipe(tap(() => this.detach(id, parentId)));
  }

  private insert(node: ArtifactNode, parentId: string | null): void {
    if (!parentId) {
      this._roots.set([...this._roots(), node]);
      return;
    }
    const parent = this.findNode(this._roots(), parentId);
    if (parent) {
      parent.children = [...(parent.children ?? []), node];
      parent.expanded = true;
    }
    this._roots.set([...this._roots()]);
  }

  private detach(id: string, parentId: string | null): void {
    if (!parentId) {
      this._roots.set(this._roots().filter((n) => n.id !== id));
      return;
    }
    const parent = this.findNode(this._roots(), parentId);
    if (parent?.children) {
      parent.children = parent.children.filter((c) => c.id !== id);
    }
    this._roots.set([...this._roots()]);
  }

  private findNode(nodes: ArtifactNode[], id: string): ArtifactNode | null {
    for (const node of nodes) {
      if (node.id === id) return node;
      if (node.children) {
        const found = this.findNode(node.children, id);
        if (found) return found;
      }
    }
    return null;
  }

  isFolderType(type: ArtifactType): boolean {
    return type === 'FOLDER';
  }
}
