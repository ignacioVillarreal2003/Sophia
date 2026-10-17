import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { DocumentWorkspaceService } from './document-workspace.service';

/**
 * Opens a workspace only for its members. Anyone else who followed a link lands on the page where they can ask the
 * admins to let them in; other failures (network, server) fall through so the workspace shows its own errors.
 */
export const workspaceAccessGuard: CanActivateFn = (route) => {
  const id = route.paramMap.get('documentWorkspaceId');
  const workspaces = inject(DocumentWorkspaceService);
  const router = inject(Router);
  if (!id) {
    return true;
  }
  return workspaces.get(id, true).pipe(
    map(() => true),
    catchError((err) =>
      of(err?.status === 403 || err?.status === 404 ? router.createUrlTree(['/document-workspaces', id, 'request-access']) : true),
    ),
  );
};
