import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthenticationService } from '@core/services/authentication/authentication.service';

export function permissionGuard(...required: string[]): CanActivateFn {
  return (): boolean | UrlTree => {
    const auth = inject(AuthenticationService);
    const router = inject(Router);
    const perms = auth.permissions();
    if (required.some((p) => perms.has(p))) {
      return true;
    }
    return router.createUrlTree(['/']);
  };
}
