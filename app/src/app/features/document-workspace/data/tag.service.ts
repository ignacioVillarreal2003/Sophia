import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { Tag } from '@core/models/document-workspace';

/** Tags of a workspace, and which of its items carry them. */
@Injectable({ providedIn: 'root' })
export class TagService {
  private readonly api = inject(ApiService);

  private base(ws: string): string {
    return `/api/v1/workspaces/${ws}`;
  }

  list(documentWorkspaceId: string): Observable<Tag[]> {
    return this.api.getPage<Tag>(`${this.base(documentWorkspaceId)}/tags`, { size: 200 }).pipe(map((page) => page.content));
  }

  create(documentWorkspaceId: string, name: string, color: string): Observable<Tag> {
    return this.api.post<Tag>(`${this.base(documentWorkspaceId)}/tags`, { name, color });
  }

  update(documentWorkspaceId: string, tagId: string, changes: { name?: string; color?: string }): Observable<Tag> {
    return this.api.patch<Tag>(`${this.base(documentWorkspaceId)}/tags/${tagId}`, changes);
  }

  remove(documentWorkspaceId: string, tagId: string): Observable<void> {
    return this.api.delete<void>(`${this.base(documentWorkspaceId)}/tags/${tagId}`);
  }

  tagsOf(documentWorkspaceId: string, artifactId: string): Observable<Tag[]> {
    return this.api.get<Tag[]>(`${this.base(documentWorkspaceId)}/artifacts/${artifactId}/tags`);
  }

  assign(documentWorkspaceId: string, artifactId: string, tagId: string): Observable<void> {
    return this.api.put<void>(`${this.base(documentWorkspaceId)}/artifacts/${artifactId}/tags/${tagId}`);
  }

  unassign(documentWorkspaceId: string, artifactId: string, tagId: string): Observable<void> {
    return this.api.delete<void>(`${this.base(documentWorkspaceId)}/artifacts/${artifactId}/tags/${tagId}`);
  }
}
