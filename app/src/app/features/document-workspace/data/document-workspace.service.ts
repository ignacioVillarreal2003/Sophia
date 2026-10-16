import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { WorkspaceUsage, AddMemberRequest, CreateDocumentWorkspaceRequest, UpdateDocumentWorkspaceRequest, DocumentWorkspace, DocumentWorkspaceListQuery, DocumentWorkspaceMember, DocumentWorkspaceRole, WorkspaceAuditEntry, WorkspaceAuditQuery } from '@core/models/document-workspace';
import { PageResponse } from '@core/models/api.model';

/**
 * DocumentWorkspace domain service. Caches the document-workspace list + active
 * document-workspace in signals so the shell/sidebar stay reactive across navigation.
 */
@Injectable({ providedIn: 'root' })
export class DocumentWorkspaceService {
  private readonly api = inject(ApiService);

  private readonly _documentWorkspaces = signal<DocumentWorkspace[]>([]);
  private readonly _activeId = signal<string | null>(null);
  private readonly _loaded = signal(false);

  readonly documentWorkspaces = this._documentWorkspaces.asReadonly();
  readonly loaded = this._loaded.asReadonly();
  readonly active = computed(
    () => this._documentWorkspaces().find((w) => w.id === this._activeId()) ?? null,
  );

  /** Primes the cache used for active-document-workspace lookups (sidebar,
   *  Trash's fallback, etc.) — not paginated UI data. `DocumentWorkspacesListComponent`
   *  uses `search()` below instead, which hits the server per page. */
  loadAll(): Observable<DocumentWorkspace[]> {
    return this.api.getPage<DocumentWorkspace>('/api/v1/workspaces').pipe(
      map((page) => page.content),
      tap((list) => {
        // Keep individually fetched workspaces (e.g. the one being viewed) that fall outside this first page.
        this._documentWorkspaces.update((prev) => [...list, ...prev.filter((w) => !list.some((l) => l.id === w.id))]);
        this._loaded.set(true);
      }),
    );
  }

  /** Real server-side search/filter/pagination for the document-workspaces
   *  list page — does not touch the `_documentWorkspaces` cache above. */
  search(query: DocumentWorkspaceListQuery): Observable<PageResponse<DocumentWorkspace>> {
    return this.api.getPage<DocumentWorkspace>('/api/v1/workspaces', query);
  }

  /** Soft-deleted workspaces owned by the caller (the trash view), one server page at a time. */
  listDeleted(page = 0, size = 20): Observable<PageResponse<DocumentWorkspace>> {
    return this.api.getPage<DocumentWorkspace>('/api/v1/workspaces/trash', { page, size });
  }

  restore(id: string): Observable<DocumentWorkspace> {
    return this.api
      .post<DocumentWorkspace>(`/api/v1/workspaces/${id}/restore`, {})
      .pipe(tap((ws) => this.upsert(ws)));
  }

  /** Permanently deletes a workspace that is already in the trash, together with its content. */
  purge(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/workspaces/${id}/purge`);
  }

  setActive(id: string | null): void {
    this._activeId.set(id);
  }

  get(id: string): Observable<DocumentWorkspace> {
    return this.api.get<DocumentWorkspace>(`/api/v1/workspaces/${id}`).pipe(
      tap((ws) => this.upsert(ws)),
    );
  }

  create(request: CreateDocumentWorkspaceRequest): Observable<DocumentWorkspace> {
    return this.api
      .post<DocumentWorkspace>('/api/v1/workspaces', request)
      .pipe(tap((ws) => this._documentWorkspaces.update((list) => [...list, ws])));
  }

  update(id: string, request: UpdateDocumentWorkspaceRequest): Observable<DocumentWorkspace> {
    return this.api.patch<DocumentWorkspace>(`/api/v1/workspaces/${id}`, request).pipe(
      tap((ws) => this.upsert(ws)),
    );
  }

  remove(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/workspaces/${id}`).pipe(
      tap(() => this._documentWorkspaces.update((list) => list.filter((w) => w.id !== id))),
    );
  }

  updateSettings(id: string, patch: Record<string, unknown>): Observable<DocumentWorkspace> {
    return this.api.patch<DocumentWorkspace>(`/api/v1/workspaces/${id}/settings`, patch).pipe(
      tap((ws) => this.upsert(ws)),
    );
  }

  uploadLogo(id: string, file: File): Observable<DocumentWorkspace> {
    const body = new FormData();
    body.append('file', file);
    return this.api.put<DocumentWorkspace>(`/api/v1/workspaces/${id}/logo`, body).pipe(tap((ws) => this.upsert(ws)));
  }

  removeLogo(id: string): Observable<DocumentWorkspace> {
    return this.api.delete<DocumentWorkspace>(`/api/v1/workspaces/${id}/logo`).pipe(tap((ws) => this.upsert(ws)));
  }

  usage(id: string): Observable<WorkspaceUsage> {
    return this.api.get<WorkspaceUsage>(`/api/v1/workspaces/${id}/usage`);
  }

  /** Workspace-scoped audit trail. Needs VIEW_AUDIT there (ADMIN role). */
  audit(
    documentWorkspaceId: string,
    query: WorkspaceAuditQuery = {},
  ): Observable<PageResponse<WorkspaceAuditEntry>> {
    return this.api.getPage<WorkspaceAuditEntry>(
      `/api/v1/workspaces/${documentWorkspaceId}/audit`,
      query,
    );
  }

  // --- Members ---
  members(documentWorkspaceId: string): Observable<DocumentWorkspaceMember[]> {
    return this.api
      .getPage<DocumentWorkspaceMember>(`/api/v1/workspaces/${documentWorkspaceId}/members`)
      .pipe(map((page) => page.content.map(normalizeMember)));
  }

  /** Only workspace admins may add members; the user must already have an account. */
  addMember(documentWorkspaceId: string, request: AddMemberRequest): Observable<DocumentWorkspaceMember> {
    return this.api
      .post<DocumentWorkspaceMember>(`/api/v1/workspaces/${documentWorkspaceId}/members`, request)
      .pipe(map(normalizeMember));
  }

  /** Owner only. */
  suspendMember(documentWorkspaceId: string, memberId: string): Observable<DocumentWorkspaceMember> {
    return this.api
      .post<DocumentWorkspaceMember>(`/api/v1/workspaces/${documentWorkspaceId}/members/${memberId}/suspend`, {})
      .pipe(map(normalizeMember));
  }

  /** Owner only. */
  reactivateMember(documentWorkspaceId: string, memberId: string): Observable<DocumentWorkspaceMember> {
    return this.api
      .post<DocumentWorkspaceMember>(`/api/v1/workspaces/${documentWorkspaceId}/members/${memberId}/reactivate`, {})
      .pipe(map(normalizeMember));
  }

  updateMemberRole(
    documentWorkspaceId: string,
    memberId: string,
    role: DocumentWorkspaceRole,
  ): Observable<DocumentWorkspaceMember> {
    return this.api
      .put<DocumentWorkspaceMember>(`/api/v1/workspaces/${documentWorkspaceId}/members/${memberId}/role`, { role })
      .pipe(map(normalizeMember));
  }

  removeMember(documentWorkspaceId: string, memberId: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/workspaces/${documentWorkspaceId}/members/${memberId}`);
  }

  private upsert(ws: DocumentWorkspace): void {
    this._documentWorkspaces.update((list) => {
      const idx = list.findIndex((w) => w.id === ws.id);
      if (idx === -1) return [...list, ws];
      const next = [...list];
      next[idx] = ws;
      return next;
    });
  }
}

/** The API names the member's display name `displayName`; the UI model calls it `fullName`. */
function normalizeMember(raw: DocumentWorkspaceMember & { displayName?: string | null }): DocumentWorkspaceMember {
  return { ...raw, fullName: raw.fullName ?? raw.displayName ?? '', photoUrl: raw.photoUrl ?? null };
}
