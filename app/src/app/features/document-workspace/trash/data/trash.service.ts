import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { Artifact } from '@core/models/document-workspace';
import { PageResponse } from '@core/models/api.model';

@Injectable({ providedIn: 'root' })
export class TrashService {
  private readonly api = inject(ApiService);
  private base(ws: string): string {
    return `/api/v1/workspaces/${ws}/trash`;
  }

  list(documentWorkspaceId: string): Observable<Artifact[]> {
    return this.api.getPage<Artifact>(this.base(documentWorkspaceId)).pipe(map((page) => page.content));
  }

  listPage(documentWorkspaceId: string, page = 0, size = 20): Observable<PageResponse<Artifact>> {
    return this.api.getPage<Artifact>(this.base(documentWorkspaceId), { page, size });
  }

  restore(documentWorkspaceId: string, artifactId: string): Observable<Artifact> {
    return this.api.post<Artifact>(`${this.base(documentWorkspaceId)}/${artifactId}/restore`);
  }

  purge(documentWorkspaceId: string, artifactId: string): Observable<void> {
    return this.api.delete<void>(`${this.base(documentWorkspaceId)}/${artifactId}`);
  }

  empty(documentWorkspaceId: string): Observable<void> {
    return this.api.delete<void>(this.base(documentWorkspaceId));
  }
}
