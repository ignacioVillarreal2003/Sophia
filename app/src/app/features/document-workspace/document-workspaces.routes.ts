import { Routes } from '@angular/router';

export const DOCUMENT_WORKSPACE_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./document-workspaces-list/document-workspaces-list.component').then(
        (m) => m.DocumentWorkspacesListComponent,
      ),
  },
  // Favorites are a filter of the main list now; the old address lands there with it applied.
  { path: 'favorites', redirectTo: '/document-workspaces?fav=true', pathMatch: 'full' },
  {
    // Workspaces other people invited the caller to; accepting makes them a member.
    path: 'invitations',
    loadComponent: () => import('./invitations/invitations.component').then((m) => m.InvitationsComponent),
  },
  // The preferences page is gone; a saved link to it lands on the list instead of being read as a workspace.
  { path: 'preferences', redirectTo: '', pathMatch: 'full' },
  {
    path: 'new',
    loadComponent: () =>
      import('./create-document-workspace/create-document-workspace.component').then(
        (m) => m.CreateDocumentWorkspaceComponent,
      ),
  },
  {
    path: 'trash',
    loadComponent: () => import('./trash/trash.component').then((m) => m.TrashComponent),
  },
  {
    path: ':documentWorkspaceId',
    loadChildren: () => import('./documents.routes').then((m) => m.DOCUMENT_ROUTES),
  },
];
