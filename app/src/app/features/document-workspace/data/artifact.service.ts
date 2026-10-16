import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { Artifact, BreadcrumbEntry, CreateArtifactRequest, MoveArtifactRequest, UpdateArtifactRequest } from '@core/models/document-workspace';
import { PageResponse } from '@core/models/api.model';

/** CRUD + navigation for artifacts (folders, documents, files, links, …). */
@Injectable({ providedIn: 'root' })
export class ArtifactService {
  private readonly api = inject(ApiService);
  private base(ws: string): string {
    return `/api/v1/workspaces/${ws}/artifacts`;
  }

  list(
    documentWorkspaceId: string,
    parentId: string | null,
    page = 0,
    size = 100,
  ): Observable<PageResponse<Artifact>> {
    return this.api.getPage<Artifact>(this.base(documentWorkspaceId), {
      parentId: parentId ?? undefined,
      page,
      size,
      sort: 'name,asc',
    });
  }

  get(documentWorkspaceId: string, artifactId: string): Observable<Artifact> {
    return this.api.get<Artifact>(`${this.base(documentWorkspaceId)}/${artifactId}`);
  }

  /** Documents (notes, checklists, spreadsheets) by name, most recently edited first. */
  documents(documentWorkspaceId: string, q = '', limit = 50): Observable<Artifact[]> {
    return this.api.get<Artifact[]>(`${this.base(documentWorkspaceId)}/documents`, { q, limit });
  }

  breadcrumb(documentWorkspaceId: string, artifactId: string): Observable<BreadcrumbEntry[]> {
    return this.api.get<BreadcrumbEntry[]>(`${this.base(documentWorkspaceId)}/${artifactId}/breadcrumb`);
  }

  create(documentWorkspaceId: string, request: CreateArtifactRequest): Observable<Artifact> {
    return this.api.post<Artifact>(this.base(documentWorkspaceId), request);
  }

  update(
    documentWorkspaceId: string,
    artifactId: string,
    request: UpdateArtifactRequest,
  ): Observable<Artifact> {
    return this.api.patch<Artifact>(`${this.base(documentWorkspaceId)}/${artifactId}`, request);
  }

  move(
    documentWorkspaceId: string,
    artifactId: string,
    request: MoveArtifactRequest,
  ): Observable<Artifact> {
    return this.api.put<Artifact>(`${this.base(documentWorkspaceId)}/${artifactId}/parent`, request);
  }

  duplicate(documentWorkspaceId: string, artifactId: string): Observable<Artifact> {
    return this.api.post<Artifact>(`${this.base(documentWorkspaceId)}/${artifactId}/duplicate`);
  }

  trash(documentWorkspaceId: string, artifactId: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/workspaces/${documentWorkspaceId}/artifacts/${artifactId}`);
  }
}
