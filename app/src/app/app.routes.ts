import {Routes} from '@angular/router';
import {authenticationGuard} from '@core/guards/authentication';
import {AUTHENTICATION_ROUTES} from '@features/authentication/authentication.routes';
import {SETTINGS_PUBLIC_ROUTES} from '@features/settings/settings.routes';

export const routes: Routes = [
  ...AUTHENTICATION_ROUTES,
  ...SETTINGS_PUBLIC_ROUTES,
  {
    // Public links open without an account, so this sits outside the guarded shell.
    path: 'p/:token',
    loadComponent: () => import('@features/public/public-view.component').then((m) => m.PublicViewComponent),
  },
  {
    path: '',
    canActivate: [authenticationGuard],
    loadComponent: () => import('@layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {path: '', pathMatch: 'full', redirectTo: 'document-workspaces'},
      {
        path: '',
        data: {workspaceKind: 'document'},
        loadChildren: () =>
          import('@features/document-workspace/document-workspace-app.routes').then(
            (m) => m.DOCUMENT_WORKSPACE_APP_ROUTES,
          ),
      },
      {
        path: 'notifications',
        data: {hideSidebar: true},
        loadComponent: () =>
          import('@features/notifications/notifications.component').then(
            (m) => m.NotificationsComponent,
          ),
      },
      {
        path: 'settings',
        data: {hideSidebar: true},
        loadChildren: () =>
          import('@features/settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
      },
      {
        path: 'help',
        data: {hideSidebar: true},
        loadComponent: () => import('@features/help/help.component').then((m) => m.HelpComponent),
      },
      {
        path: 'admin',
        data: {hideSidebar: true},
        loadChildren: () => import('@features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
    ],
  },
  {
    path: 'error/403',
    data: {
      titleKey: 'errors.forbidden.title',
      descriptionKey: 'errors.forbidden.description',
      actionLabelKey: 'errors.forbidden.backHome',
      actionLink: '/',
    },
    loadComponent: () =>
      import('@features/errors/error-page.component').then((m) => m.ErrorPageComponent),
  },
  {
    path: 'error/404',
    data: {
      titleKey: 'errors.notFound.title',
      descriptionKey: 'errors.notFound.description',
      actionLabelKey: 'errors.notFound.goBack',
    },
    loadComponent: () =>
      import('@features/errors/error-page.component').then((m) => m.ErrorPageComponent),
  },
  {
    path: '**',
    data: {
      titleKey: 'errors.notFound.title',
      descriptionKey: 'errors.notFound.description',
      actionLabelKey: 'errors.notFound.goBack',
    },
    loadComponent: () =>
      import('@features/errors/error-page.component').then((m) => m.ErrorPageComponent),
  },
];
