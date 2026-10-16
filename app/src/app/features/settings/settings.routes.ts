import {Routes} from '@angular/router';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./settings-layout/settings-layout.component').then((m) => m.SettingsLayoutComponent),
    children: [
      {path: '', pathMatch: 'full', redirectTo: 'profile'},
      {
        path: 'profile',
        loadComponent: () => import('./profile/profile.component').then((m) => m.ProfileComponent),
      },
      {
        path: 'appearance',
        loadComponent: () =>
          import('./appearance/appearance.component').then((m) => m.AppearanceComponent),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./notifications/notification-preferences.component').then(
            (m) => m.NotificationPreferencesComponent,
          ),
      },
      {
        path: 'security',
        loadComponent: () =>
          import('./security/security.component').then((m) => m.SecurityComponent),
      },
      {
        path: 'sessions',
        loadComponent: () =>
          import('./sessions/sessions.component').then((m) => m.SessionsComponent),
      },
      {
        path: 'activity',
        loadComponent: () =>
          import('./activity/activity.component').then((m) => m.ActivityComponent),
      },
    ],
  },
];

export const SETTINGS_PUBLIC_ROUTES: Routes = [
  {
    path: 'reset-password',
    loadComponent: () =>
      import('@layout/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./reset-password/reset-password.component').then(
            (m) => m.ResetPasswordComponent,
          ),
      },
    ],
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('@layout/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./verify-email/verify-email.component').then((m) => m.VerifyEmailComponent),
      },
    ],
  },
  {
    path: 'confirm-email-change',
    loadComponent: () =>
      import('@layout/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./confirm-email-change/confirm-email-change.component').then(
            (m) => m.ConfirmEmailChangeComponent,
          ),
      },
    ],
  },
];
