import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { PageResponse } from '@core/models/api.model';
import { Artifact, Favorite } from '@core/models/document-workspace';

/** Favorites (pinned) + recent-activity feed, both scoped to a documentWorkspace. */
@Injectable({ providedIn: 'root' })
export class FavoriteService {
  private readonly api = inject(ApiService);

  favorites(documentWorkspaceId: string, size = 100): Observable<Favorite[]> {
    return this.api
      .getPage<Favorite>(`/api/v1/workspaces/${documentWorkspaceId}/favorites`, { size })
      .pipe(map((page) => page.content));
  }

  /** One page of favorites, newest first, with the total so it can be paged. */
  favoritesPage(documentWorkspaceId: string, page: number, size: number): Observable<PageResponse<Favorite>> {
    return this.api.getPage<Favorite>(`/api/v1/workspaces/${documentWorkspaceId}/favorites`, { page, size });
  }

  recent(documentWorkspaceId: string, limit = 12): Observable<Artifact[]> {
    return this.api.get<Artifact[]>(`/api/v1/workspaces/${documentWorkspaceId}/recent`, { limit });
  }

  add(documentWorkspaceId: string, artifactId: string): Observable<void> {
    return this.api.put<void>(`/api/v1/workspaces/${documentWorkspaceId}/favorites/${artifactId}`);
  }

  remove(documentWorkspaceId: string, artifactId: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/workspaces/${documentWorkspaceId}/favorites/${artifactId}`);
  }

  setPinned(documentWorkspaceId: string, artifactId: string, pinned: boolean): Observable<void> {
    return this.api.put<void>(
      `/api/v1/workspaces/${documentWorkspaceId}/favorites/${artifactId}/pinned`,
      { pinned },
    );
  }
}
