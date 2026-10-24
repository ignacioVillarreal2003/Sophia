import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { PageResponse } from '@core/models/api.model';

export type WorkspaceStatusValue = 'ACTIVE' | 'READ_ONLY' | 'SUSPENDED';

export interface AdminWorkspace {
  readonly id: string;
  readonly name: string;
  readonly color: string | null;
  readonly logoUrl: string | null;
  readonly ownerId: string;
  readonly ownerEmail: string | null;
  readonly status: WorkspaceStatusValue;
  readonly memberCount: number;
  readonly usedBytes: number;
  readonly storageQuotaMb: number | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AdminWorkspaceDetail {
  readonly workspace: AdminWorkspace;
  readonly description: string | null;
  readonly language: string;
  readonly timezone: string;
  readonly dateFormat: string;
  readonly trashRetentionDays: number | null;
  readonly publicSharingEnabled: boolean;
  readonly updatedBy: string | null;
  readonly stats: { readonly items: number; readonly trashedItems: number };
  readonly members: {
    readonly userId: string;
    readonly email: string;
    readonly displayName: string | null;
    readonly role: string;
    readonly status: string;
    readonly joinedAt: string;
  }[];
}

/** Platform-wide workspace administration (needs the WORKSPACE_MANAGE permission). */
@Injectable({ providedIn: 'root' })
export class AdminWorkspaceService {
  private readonly api = inject(ApiService);

  list(query: { search?: string; status?: string; page?: number }): Observable<PageResponse<AdminWorkspace>> {
    return this.api.getPage<AdminWorkspace>('/api/v1/admin/workspaces', {
      search: query.search || undefined,
      status: query.status || undefined,
      page: query.page ?? 0,
      size: 20,
    });
  }

  detail(id: string): Observable<AdminWorkspaceDetail> {
    return this.api.get<AdminWorkspaceDetail>(`/api/v1/admin/workspaces/${id}`);
  }

  setStatus(id: string, status: WorkspaceStatusValue): Observable<AdminWorkspace> {
    return this.api.put<AdminWorkspace>(`/api/v1/admin/workspaces/${id}/status`, { status });
  }

  /** `0` removes the limit. */
  setStorageQuota(id: string, storageQuotaMb: number): Observable<AdminWorkspace> {
    return this.api.put<AdminWorkspace>(`/api/v1/admin/workspaces/${id}/limits`, { storageQuotaMb });
  }
}
