import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';

export interface PublicLink {
  readonly id: string;
  readonly artifactId: string;
  readonly createdBy: string;
  readonly createdAt: string;
  readonly expiresAt: string | null;
  /** Present only in the response to creating the link. */
  readonly token?: string | null;
}

/** Read-only public links to one artifact (anyone with the link can read it, no account needed). */
@Injectable({ providedIn: 'root' })
export class PublicLinkService {
  private readonly api = inject(ApiService);

  private base(ws: string, artifact: string): string {
    return `/api/v1/workspaces/${ws}/artifacts/${artifact}/public-links`;
  }

  list(documentWorkspaceId: string, artifactId: string): Observable<PublicLink[]> {
    return this.api.get<PublicLink[]>(this.base(documentWorkspaceId, artifactId));
  }

  create(documentWorkspaceId: string, artifactId: string, expiresInDays: number | null): Observable<PublicLink> {
    return this.api.post<PublicLink>(this.base(documentWorkspaceId, artifactId), { expiresInDays });
  }

  /** How many public links of the whole workspace currently work. */
  activeCount(documentWorkspaceId: string): Observable<number> {
    return this.api
      .get<{ count: number }>(`/api/v1/workspaces/${documentWorkspaceId}/public-links/active-count`)
      .pipe(map((r) => r.count));
  }

  /** "Stop sharing everything": revokes every public link of the workspace; returns how many it revoked. */
  revokeAll(documentWorkspaceId: string): Observable<number> {
    return this.api
      .delete<{ revoked: number }>(`/api/v1/workspaces/${documentWorkspaceId}/public-links`)
      .pipe(map((r) => r.revoked));
  }

  revoke(documentWorkspaceId: string, artifactId: string, linkId: string): Observable<void> {
    return this.api.delete<void>(`${this.base(documentWorkspaceId, artifactId)}/${linkId}`);
  }
}
