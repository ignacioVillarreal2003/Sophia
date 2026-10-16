import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthenticationService } from '@core/services/authentication/authentication.service';

export const authenticationGuard: CanActivateFn = (_route, state): boolean | UrlTree => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/authentication/login'], { queryParams: { returnUrl: state.url } });
};
