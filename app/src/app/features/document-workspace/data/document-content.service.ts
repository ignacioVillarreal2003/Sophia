import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { DocumentContent, DocumentVersion } from '@core/models/document-workspace';

/** Text content and version history of Markdown notes, checklists and spreadsheets. */
@Injectable({ providedIn: 'root' })
export class DocumentContentService {
  private readonly api = inject(ApiService);

  private base(ws: string, doc: string): string {
    return `/api/v1/workspaces/${ws}/documents/${doc}`;
  }

  get(ws: string, doc: string): Observable<DocumentContent> {
    return this.api.get<DocumentContent>(`${this.base(ws, doc)}/content`);
  }

  /** Answers 409 when the document changed since `baseVersion`. */
  save(ws: string, doc: string, content: string, baseVersion: number): Observable<DocumentContent> {
    return this.api.put<DocumentContent>(`${this.base(ws, doc)}/content`, { content, baseVersion });
  }

  versions(ws: string, doc: string, namedOnly = false): Observable<DocumentVersion[]> {
    return this.api
      .getPage<DocumentVersion>(`${this.base(ws, doc)}/versions`, { namedOnly, size: 100 })
      .pipe(map((page) => page.content));
  }

  version(ws: string, doc: string, versionId: string): Observable<DocumentVersion> {
    return this.api.get<DocumentVersion>(`${this.base(ws, doc)}/versions/${versionId}`);
  }

  restore(ws: string, doc: string, versionId: string): Observable<DocumentContent> {
    return this.api.post<DocumentContent>(`${this.base(ws, doc)}/versions/${versionId}/restore`);
  }

  nameVersion(ws: string, doc: string, versionId: string, name: string): Observable<DocumentVersion> {
    return this.api.put<DocumentVersion>(`${this.base(ws, doc)}/versions/${versionId}/name`, { name });
  }
}
