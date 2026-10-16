import { Routes } from '@angular/router';

export const DOCUMENT_WORKSPACE_APP_ROUTES: Routes = [
  {
    path: 'document-workspaces',
    loadChildren: () => import('./document-workspaces.routes').then((m) => m.DOCUMENT_WORKSPACE_ROUTES),
  },
  {
    path: 'search',
    loadComponent: () => import('./search/search.component').then((m) => m.SearchComponent),
  },
  {
    path: 'templates',
    loadComponent: () => import('./templates/templates.component').then((m) => m.TemplatesComponent),
  },
];
