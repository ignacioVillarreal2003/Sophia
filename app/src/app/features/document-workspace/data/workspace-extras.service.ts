import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { ApiService as NotificationApi } from '@core/services/notification/api.service';
import { silentErrors } from '@core/services/http-context';
import { PageResponse } from '@core/models/api.model';
import {
  AccessPreview,
  AccessRequest,
  DocumentWorkspaceRole,
  StorageRequest,
  WorkspaceActivityItem,
} from '@core/models/document-workspace';

/** Access requests (non-members asking in) and storage requests (owners asking for more space). */
@Injectable({ providedIn: 'root' })
export class WorkspaceRequestsService {
  private readonly api = inject(ApiService);

  // --- access
  accessPreview(workspaceId: string): Observable<AccessPreview> {
    return this.api.get<AccessPreview>(`/api/v1/workspaces/${workspaceId}/access-request`, undefined, silentErrors());
  }

  requestAccess(workspaceId: string, message: string): Observable<AccessRequest> {
    return this.api.post<AccessRequest>(`/api/v1/workspaces/${workspaceId}/access-request`, { message });
  }

  pendingAccess(workspaceId: string): Observable<AccessRequest[]> {
    return this.api.get<AccessRequest[]>(`/api/v1/workspaces/${workspaceId}/access-requests`);
  }

  approveAccess(workspaceId: string, requestId: string, role: DocumentWorkspaceRole): Observable<AccessRequest> {
    return this.api.post<AccessRequest>(
      `/api/v1/workspaces/${workspaceId}/access-requests/${requestId}/approve`,
      { role },
    );
  }

  denyAccess(workspaceId: string, requestId: string): Observable<AccessRequest> {
    return this.api.post<AccessRequest>(`/api/v1/workspaces/${workspaceId}/access-requests/${requestId}/deny`);
  }

  // --- storage (owner)
  latestStorageRequest(workspaceId: string): Observable<StorageRequest | null> {
    return this.api.get<StorageRequest | null>(`/api/v1/workspaces/${workspaceId}/storage-request`);
  }

  requestStorage(workspaceId: string, requestedMb: number, message: string): Observable<StorageRequest> {
    return this.api.post<StorageRequest>(`/api/v1/workspaces/${workspaceId}/storage-request`, {
      requestedMb,
      message,
    });
  }

  // --- storage (platform admin)
  storageRequests(status: string, page = 0, size = 20): Observable<PageResponse<StorageRequest>> {
    return this.api.getPage<StorageRequest>('/api/v1/admin/storage-requests', { status, page, size });
  }

  pendingStorageCount(): Observable<{ count: number }> {
    return this.api.get<{ count: number }>('/api/v1/admin/storage-requests/pending-count', undefined, silentErrors());
  }

  approveStorage(requestId: string, grantedMb: number): Observable<StorageRequest> {
    return this.api.post<StorageRequest>(`/api/v1/admin/storage-requests/${requestId}/approve`, { grantedMb });
  }

  denyStorage(requestId: string): Observable<StorageRequest> {
    return this.api.post<StorageRequest>(`/api/v1/admin/storage-requests/${requestId}/deny`);
  }
}

/** What happened across the caller's workspaces, and how many unread notifications each one has. */
@Injectable({ providedIn: 'root' })
export class WorkspaceActivityService {
  private readonly api = inject(ApiService);
  private readonly notificationApi = inject(NotificationApi);

  private readonly _unread = signal<Record<string, number>>({});
  readonly unreadByWorkspace = this._unread.asReadonly();
  readonly totalUnread = computed(() => Object.values(this._unread()).reduce((sum, n) => sum + n, 0));

  recent(limit = 30, includeOwn = false): Observable<WorkspaceActivityItem[]> {
    return this.api.get<WorkspaceActivityItem[]>('/api/v1/workspace-activity', { limit, includeOwn });
  }

  refreshUnread(): Observable<Record<string, number>> {
    return this.notificationApi
      .get<Record<string, number>>('/api/v1/notifications/unread-by-workspace')
      .pipe(tap((counts) => this._unread.set(counts)));
  }
}
