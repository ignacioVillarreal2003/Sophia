import { HttpClient, HttpEventType } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, map } from 'rxjs';
import { Artifact, FileVersion } from '@core/models/document-workspace';
import { ApiResponse } from '@core/models/api.model';

/** Upload progress event surfaced to the UI. */
export type UploadEvent =
  | { type: 'progress'; percent: number }
  | { type: 'done'; artifact: Artifact };

interface DownloadUrlResponse {
  url: string;
  expiresAt?: string;
}

/**
 * File operations. Uploads bypass ApiService to observe HttpEvents for a real
 * progress bar; the response envelope is unwrapped manually.
 */
@Injectable({ providedIn: 'root' })
export class FileService {
  private readonly http = inject(HttpClient);
  private base(ws: string): string {
    return `${environment.documentWorkspaceApiUrl}/api/v1/workspaces/${ws}/files`;
  }

  upload(
    documentWorkspaceId: string,
    file: File,
    parentId?: string | null,
  ): Observable<UploadEvent> {
    const form = new FormData();
    form.append('file', file, file.name);

    let url = this.base(documentWorkspaceId);
    const params = new URLSearchParams();
    if (parentId) params.set('parentId', parentId);
    if ([...params].length) url += `?${params.toString()}`;

    return this.http
      .post<ApiResponse<Artifact>>(url, form, { reportProgress: true, observe: 'events' })
      .pipe(
        map((event): UploadEvent => {
          if (event.type === HttpEventType.UploadProgress) {
            const percent = event.total ? Math.round((event.loaded / event.total) * 100) : 0;
            return { type: 'progress', percent };
          }
          if (event.type === HttpEventType.Response) {
            return { type: 'done', artifact: (event.body as ApiResponse<Artifact>).data };
          }
          return { type: 'progress', percent: 0 };
        }),
      );
  }

  downloadUrl(documentWorkspaceId: string, artifactId: string): Observable<string> {
    return this.http
      .get<ApiResponse<DownloadUrlResponse>>(`${this.base(documentWorkspaceId)}/${artifactId}/download`)
      .pipe(map((res) => res.data.url));
  }

  versions(documentWorkspaceId: string, artifactId: string): Observable<FileVersion[]> {
    return this.http
      .get<ApiResponse<FileVersion[]>>(`${this.base(documentWorkspaceId)}/${artifactId}/versions`)
      .pipe(map((res) => res.data));
  }

  restoreVersion(
    documentWorkspaceId: string,
    artifactId: string,
    versionId: string,
  ): Observable<Artifact> {
    return this.http
      .post<ApiResponse<Artifact>>(
        `${this.base(documentWorkspaceId)}/${artifactId}/versions/${versionId}/restore`,
        {},
      )
      .pipe(map((res) => res.data));
  }
}
