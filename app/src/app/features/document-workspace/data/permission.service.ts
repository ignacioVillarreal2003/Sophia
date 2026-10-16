import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { AclEntry, GrantPermissionRequest } from '@core/models/document-workspace';

/** Per-artifact discretionary ACL grants (ResourcePermissionController). */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private readonly api = inject(ApiService);
  private base(ws: string, artifact: string): string {
    return `/api/v1/workspaces/${ws}/artifacts/${artifact}/permissions`;
  }

  list(documentWorkspaceId: string, artifactId: string): Observable<AclEntry[]> {
    return this.api.get<AclEntry[]>(this.base(documentWorkspaceId, artifactId));
  }

  grant(
    documentWorkspaceId: string,
    artifactId: string,
    request: GrantPermissionRequest,
  ): Observable<AclEntry> {
    return this.api.post<AclEntry>(this.base(documentWorkspaceId, artifactId), request);
  }

  revoke(documentWorkspaceId: string, artifactId: string, aclEntryId: string): Observable<void> {
    return this.api.delete<void>(`${this.base(documentWorkspaceId, artifactId)}/${aclEntryId}`);
  }
}
