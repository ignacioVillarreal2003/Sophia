import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { Artifact } from '@core/models/document-workspace';
import { Template } from '@core/models/document-workspace';

@Injectable({ providedIn: 'root' })
export class TemplateService {
  private readonly api = inject(ApiService);
  private base(ws: string): string {
    return `/api/v1/workspaces/${ws}/templates`;
  }

  list(documentWorkspaceId: string): Observable<Template[]> {
    return this.api.get<Template[]>(this.base(documentWorkspaceId));
  }

  instantiate(documentWorkspaceId: string, templateId: string, parentId?: string): Observable<Artifact> {
    return this.api.post<Artifact>(`${this.base(documentWorkspaceId)}/${templateId}/instantiate`, {
      parentId,
    });
  }

  publish(documentWorkspaceId: string, templateId: string): Observable<Template> {
    return this.api.post<Template>(`${this.base(documentWorkspaceId)}/${templateId}/publish`);
  }

  unpublish(documentWorkspaceId: string, templateId: string): Observable<Template> {
    return this.api.post<Template>(`${this.base(documentWorkspaceId)}/${templateId}/unpublish`);
  }
}
