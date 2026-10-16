import { Routes } from '@angular/router';
import { guestGuard } from '@core/guards/authentication';

export const AUTHENTICATION_ROUTES: Routes = [
  {
    path: 'authentication',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('@layout/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'login' },
      {
        path: 'login',
        loadComponent: () => import('./login/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'register',
        loadComponent: () => import('./register/register.component').then((m) => m.RegisterComponent),
      },
      {
        path: 'forgot-password',
        loadComponent: () =>
          import('./forgot-password/forgot-password.component').then((m) => m.ForgotPasswordComponent),
      },
    ],
  },
];
