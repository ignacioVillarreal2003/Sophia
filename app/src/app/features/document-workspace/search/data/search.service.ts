import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { SearchResult } from '@core/models/document-workspace';
import { silentErrors } from '@core/services/http-context';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly api = inject(ApiService);

  search(documentWorkspaceId: string, query: string, type?: string): Observable<SearchResult> {
    return this.api.get<SearchResult>(
      `/api/v1/workspaces/${documentWorkspaceId}/search`,
      { q: query, type },
      silentErrors(),
    );
  }
}
