import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, map } from 'rxjs';
import { ExportFormat } from '@core/models/document-workspace';
import { ApiResponse } from '@core/models/api.model';

const EXTENSIONS: Record<ExportFormat, string> = {
  MARKDOWN: 'md',
  HTML: 'html',
  JSON: 'json',
  PDF: 'pdf',
  DOCX: 'docx',
  TXT: 'txt',
  CSV: 'csv',
  XLSX: 'xlsx',
};

/** Export an artifact to a file, or import a file/archive into a documentWorkspace. */
@Injectable({ providedIn: 'root' })
export class ExportImportService {
  private readonly http = inject(HttpClient);
  private base(ws: string): string {
    return `${environment.documentWorkspaceApiUrl}/api/v1/workspaces/${ws}`;
  }

  /** Downloads the export as a blob and triggers a browser save. */
  export(documentWorkspaceId: string, artifactId: string, format: ExportFormat, name: string): Observable<void> {
    return this.http
      .get(`${this.base(documentWorkspaceId)}/artifacts/${artifactId}/export`, {
        params: { format },
        responseType: 'blob',
      })
      .pipe(map((blob) => this.saveBlob(blob, `${name}.${EXTENSIONS[format]}`)));
  }

  /** Imports a file/zip; returns the ids of created artifacts, grouped. */
  import(
    documentWorkspaceId: string,
    file: File,
    parentId?: string | null,
  ): Observable<Record<string, string[]>> {
    const form = new FormData();
    form.append('file', file, file.name);
    let url = `${this.base(documentWorkspaceId)}/import`;
    if (parentId) {
      url += `?parentId=${parentId}`;
    }
    return this.http
      .post<ApiResponse<Record<string, string[]>>>(url, form)
      .pipe(map((res) => res.data));
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
