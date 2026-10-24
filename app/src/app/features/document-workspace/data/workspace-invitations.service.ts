import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AddMemberRequest, DocumentWorkspaceRole, WorkspaceInvitation } from '@core/models/document-workspace';
import { PageResponse } from '@core/models/api.model';
import { ApiService } from '@core/services/document-workspace/api.service';

/** One choice offered by a picker, with what it is called. */
export interface FacetOption {
  readonly value: string;
  readonly label: string;
}

export type InvitationSort = 'AT' | 'NAME' | 'EXPIRES';

/** How the invitations received are searched, narrowed, ordered and paged; the server does all of it. */
export interface InvitationQuery {
  /** Part of the workspace name. */
  readonly q: string;
  readonly role: DocumentWorkspaceRole | '';
  readonly invitedBy: string;
  readonly from: string;
  readonly to: string;
  readonly sort: InvitationSort;
  readonly desc: boolean;
  readonly page: number;
}

export const INVITATIONS_PAGE_SIZE = 20;

/** Inviting people to a workspace and answering the invitations the caller received. */
@Injectable({ providedIn: 'root' })
export class WorkspaceInvitationsService {
  private readonly api = inject(ApiService);

  /** The caller's own pending invitations. */
  search(query: InvitationQuery): Observable<PageResponse<WorkspaceInvitation>> {
    return this.api.getPage<WorkspaceInvitation>('/api/v1/invitations', {
      q: query.q.trim() || undefined,
      role: query.role || undefined,
      invitedBy: query.invitedBy || undefined,
      from: query.from || undefined,
      to: query.to || undefined,
      sort: query.sort,
      desc: query.desc,
      page: query.page,
      size: INVITATIONS_PAGE_SIZE,
    });
  }

  /** People who invited the caller, matching the text (for a picker; the server returns a few). */
  inviters(text: string): Observable<FacetOption[]> {
    return this.api.get<FacetOption[]>('/api/v1/invitations/inviters', { q: text });
  }

  accept(id: string): Observable<WorkspaceInvitation> {
    return this.api.post<WorkspaceInvitation>(`/api/v1/invitations/${id}/accept`);
  }

  decline(id: string): Observable<void> {
    return this.api.post<void>(`/api/v1/invitations/${id}/decline`);
  }

  // --- admins of a workspace
  invite(workspaceId: string, request: AddMemberRequest): Observable<WorkspaceInvitation> {
    return this.api.post<WorkspaceInvitation>(`/api/v1/workspaces/${workspaceId}/invitations`, request);
  }

  pending(workspaceId: string): Observable<WorkspaceInvitation[]> {
    return this.api.get<WorkspaceInvitation[]>(`/api/v1/workspaces/${workspaceId}/invitations`);
  }

  changeRole(workspaceId: string, id: string, role: DocumentWorkspaceRole): Observable<WorkspaceInvitation> {
    return this.api.patch<WorkspaceInvitation>(`/api/v1/workspaces/${workspaceId}/invitations/${id}`, { role });
  }

  revoke(workspaceId: string, id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/workspaces/${workspaceId}/invitations/${id}`);
  }
}
