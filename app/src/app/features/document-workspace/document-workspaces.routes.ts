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
