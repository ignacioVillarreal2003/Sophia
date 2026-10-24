import { Routes } from '@angular/router';
import { workspaceAccessGuard } from './data/workspace-access.guard';

/**
 * Routes nested under /workspaces/:documentWorkspaceId. The document-workspace tree
 * layout hosts the artifact sidebar; children swap between the folder
 * explorer and the document editor.
 */
export const DOCUMENT_ROUTES: Routes = [
  {
    // Where non-members land (see workspaceAccessGuard): they can ask the admins to let them in.
    path: 'request-access',
    data: { hideSidebar: true },
    loadComponent: () =>
      import('./request-access/request-access.component').then((m) => m.RequestAccessComponent),
  },
  {
    path: '',
    canActivate: [workspaceAccessGuard],
    loadComponent: () =>
      import('./document-workspace-tree-layout/document-workspace-tree-layout.component').then(
        (m) => m.DocumentWorkspaceTreeLayoutComponent,
      ),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./explorer/explorer.component').then((m) => m.ExplorerComponent),
      },
      {
        path: 'folder/:folderId',
        loadComponent: () =>
          import('./explorer/explorer.component').then((m) => m.ExplorerComponent),
      },
      {
        path: 'a/:artifactId',
        loadComponent: () =>
          import('./document/document-view.component').then((m) => m.DocumentViewComponent),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('./new-artifact/new-artifact.component').then((m) => m.NewArtifactComponent),
      },
      {
        path: 'details/:artifactId',
        loadComponent: () =>
          import('./details/artifact-details.component').then((m) => m.ArtifactDetailsComponent),
      },
      {
        path: 'ai',
        loadComponent: () => import('../ai/ai-hub/ai-hub.component').then((m) => m.AiHubComponent),
      },
      {
        path: 'tags/new',
        loadComponent: () => import('./tags/new-tag.component').then((m) => m.NewTagComponent),
      },
      {
        path: 'tags',
        loadComponent: () => import('./tags/tags.component').then((m) => m.TagsComponent),
      },
      {
        path: 'favorites',
        loadComponent: () => import('./starred/starred.component').then((m) => m.StarredComponent),
      },
      // The page used to be called "starred"; saved links keep working.
      { path: 'starred', redirectTo: 'favorites', pathMatch: 'full' },
      {
        path: 'activity',
        loadComponent: () =>
          import('./activity/workspace-activity.component').then((m) => m.WorkspaceActivityComponent),
      },
      {
        path: 'trash',
        loadComponent: () =>
          import('./workspace-trash/workspace-trash.component').then((m) => m.WorkspaceTrashComponent),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./document-workspace-settings/document-workspace-settings.component').then(
            (m) => m.DocumentWorkspaceSettingsComponent,
          ),
      },
      {
        path: 'members',
        loadComponent: () =>
          import('./members/members.component').then((m) => m.MembersComponent),
      },
    ],
  },
];
