import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditLogEntry, AuditLogQuery } from '@core/models/authentication';
import { BulkOperationResponse, PageQuery, PageResponse } from '@core/models/api.model';
import { ApiService } from '@core/services/authentication/api.service';
import {
  Permission,
  PermissionListQuery,
  PermissionRequest,
  Role,
  RoleListQuery,
  RoleRequest,
  ServiceClient,
  ServiceClientListQuery,
  ServiceClientRequest,
  UpdateUserPermissionsRequest,
  UpdateUserRolesRequest,
  UpdateUserStatusRequest,
  UserCreateRequest,
  UserListQuery,
  UserPatchRequest,
  UserUpdateRequest,
} from '@core/models/admin';
import { Session, User } from '@core/models/authentication';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly api = inject(ApiService);

  listAuditLogs(query: AuditLogQuery): Observable<PageResponse<AuditLogEntry>> {
    return this.api.getPage<AuditLogEntry>('/api/v1/audit-logs', query);
  }

  listPermissions(query?: PermissionListQuery): Observable<PageResponse<Permission>> {
    return this.api.getPage<Permission>('/api/v1/permissions', query);
  }

  getPermission(id: string): Observable<Permission> {
    return this.api.get<Permission>(`/api/v1/permissions/${id}`);
  }

  createPermission(request: PermissionRequest): Observable<Permission> {
    return this.api.post<Permission>('/api/v1/permissions', request);
  }

  updatePermission(id: string, request: PermissionRequest): Observable<Permission> {
    return this.api.put<Permission>(`/api/v1/permissions/${id}`, request);
  }

  deletePermission(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/permissions/${id}`);
  }

  bulkDeletePermissions(ids: string[]): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/permissions/bulk-delete', { ids });
  }

  listRoles(query?: RoleListQuery): Observable<PageResponse<Role>> {
    return this.api.getPage<Role>('/api/v1/roles', query);
  }

  getRole(id: string): Observable<Role> {
    return this.api.get<Role>(`/api/v1/roles/${id}`);
  }

  createRole(request: RoleRequest): Observable<Role> {
    return this.api.post<Role>('/api/v1/roles', request);
  }

  updateRole(id: string, request: RoleRequest): Observable<Role> {
    return this.api.put<Role>(`/api/v1/roles/${id}`, request);
  }

  deleteRole(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/roles/${id}`);
  }

  bulkDeleteRoles(ids: string[]): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/roles/bulk-delete', { ids });
  }

  listServiceClients(query?: ServiceClientListQuery): Observable<PageResponse<ServiceClient>> {
    return this.api.getPage<ServiceClient>('/api/v1/service-clients', query);
  }

  getServiceClient(id: string): Observable<ServiceClient> {
    return this.api.get<ServiceClient>(`/api/v1/service-clients/${id}`);
  }

  createServiceClient(request: ServiceClientRequest): Observable<ServiceClient> {
    return this.api.post<ServiceClient>('/api/v1/service-clients', request);
  }

  updateServiceClient(id: string, request: ServiceClientRequest): Observable<ServiceClient> {
    return this.api.put<ServiceClient>(`/api/v1/service-clients/${id}`, request);
  }

  rotateServiceClientSecret(id: string): Observable<ServiceClient> {
    return this.api.post<ServiceClient>(`/api/v1/service-clients/${id}/rotate-secret`);
  }

  deleteServiceClient(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/service-clients/${id}`);
  }

  bulkDeleteServiceClients(ids: string[]): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/service-clients/bulk-delete', { ids });
  }

  listUsers(query: UserListQuery): Observable<PageResponse<User>> {
    return this.api.getPage<User>('/api/v1/users', query);
  }

  getUser(id: string): Observable<User> {
    return this.api.get<User>(`/api/v1/users/${id}`);
  }

  createUser(request: UserCreateRequest): Observable<User> {
    return this.api.post<User>('/api/v1/users', request);
  }

  updateUser(id: string, request: UserUpdateRequest): Observable<User> {
    return this.api.put<User>(`/api/v1/users/${id}`, request);
  }

  patchUser(id: string, request: UserPatchRequest): Observable<User> {
    return this.api.patch<User>(`/api/v1/users/${id}`, request);
  }

  deleteUser(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/users/${id}`);
  }

  updateUserRoles(id: string, request: UpdateUserRolesRequest): Observable<User> {
    return this.api.patch<User>(`/api/v1/users/${id}/roles`, request);
  }

  updateUserPermissions(id: string, request: UpdateUserPermissionsRequest): Observable<User> {
    return this.api.patch<User>(`/api/v1/users/${id}/permissions`, request);
  }

  updateUserStatus(id: string, request: UpdateUserStatusRequest): Observable<User> {
    return this.api.patch<User>(`/api/v1/users/${id}/status`, request);
  }

  setUserPassword(id: string, newPassword: string): Observable<void> {
    return this.api.patch<void>(`/api/v1/users/${id}/password`, { newPassword });
  }

  listUserSessions(id: string, query: PageQuery = {}): Observable<PageResponse<Session>> {
    return this.api.getPage<Session>(`/api/v1/users/${id}/sessions`, query);
  }

  revokeUserSession(id: string, sessionId: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/users/${id}/sessions/${sessionId}`);
  }

  revokeAllUserSessions(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/users/${id}/sessions`);
  }

  resendUserVerification(id: string): Observable<void> {
    return this.api.post<void>(`/api/v1/users/${id}/resend-verification`);
  }

  verifyUserEmail(id: string): Observable<void> {
    return this.api.patch<void>(`/api/v1/users/${id}/verify-email`);
  }

  unverifyUserEmail(id: string): Observable<void> {
    return this.api.patch<void>(`/api/v1/users/${id}/unverify-email`);
  }

  unlinkUserGoogle(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/users/${id}/google`);
  }

  bulkDeleteUsers(ids: string[]): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/users/bulk-delete', { ids });
  }

  bulkUpdateUserStatus(ids: string[], active: boolean): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/users/bulk-status', { ids, active });
  }

  bulkUpdateUserRoles(ids: string[], roles: string[]): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/users/bulk-roles', { ids, roles });
  }

  bulkAddUserPermissions(ids: string[], permissions: string[]): Observable<BulkOperationResponse> {
    return this.api.post<BulkOperationResponse>('/api/v1/users/bulk-permissions', { ids, permissions });
  }
}
