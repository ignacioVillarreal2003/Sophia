import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthenticationService } from '@core/services/authentication/authentication.service';

export const guestGuard: CanActivateFn = (): boolean | UrlTree => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/']) : true;
};
