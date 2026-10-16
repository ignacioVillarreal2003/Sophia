import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '@core/services/document-workspace/api.service';
import { Comment, CreateCommentRequest } from '@core/models/document-workspace';

/** Threaded comments on an artifact (1-level replies, reactions, resolve). */
@Injectable({ providedIn: 'root' })
export class CommentService {
  private readonly api = inject(ApiService);
  private base(ws: string): string {
    return `/api/v1/workspaces/${ws}`;
  }

  list(documentWorkspaceId: string, artifactId: string): Observable<Comment[]> {
    return this.api
      .getPage<Comment>(`${this.base(documentWorkspaceId)}/artifacts/${artifactId}/comments`)
      .pipe(map((page) => page.content));
  }

  create(
    documentWorkspaceId: string,
    artifactId: string,
    request: CreateCommentRequest,
  ): Observable<Comment> {
    return this.api.post<Comment>(
      `${this.base(documentWorkspaceId)}/artifacts/${artifactId}/comments`,
      request,
    );
  }

  update(documentWorkspaceId: string, commentId: string, body: string): Observable<Comment> {
    return this.api.patch<Comment>(`${this.base(documentWorkspaceId)}/comments/${commentId}`, { body });
  }

  resolve(documentWorkspaceId: string, commentId: string): Observable<Comment> {
    return this.api.post<Comment>(`${this.base(documentWorkspaceId)}/comments/${commentId}/resolve`);
  }

  unresolve(documentWorkspaceId: string, commentId: string): Observable<Comment> {
    return this.api.post<Comment>(`${this.base(documentWorkspaceId)}/comments/${commentId}/unresolve`);
  }

  react(documentWorkspaceId: string, commentId: string, emoji: string): Observable<Comment> {
    return this.api.post<Comment>(`${this.base(documentWorkspaceId)}/comments/${commentId}/reactions`, {
      emoji,
    });
  }

  remove(documentWorkspaceId: string, commentId: string): Observable<void> {
    return this.api.delete<void>(`${this.base(documentWorkspaceId)}/comments/${commentId}`);
  }
}
