import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { TokenStorageService } from '@core/services/authentication/token-storage.service';
import { SKIP_AUTH } from '@core/services/http-context';

/**
 * Every Sophia API the bearer token may be attached to. Adding a service to the
 * environment is not enough — it has to be listed here too, or its calls go out
 * unauthenticated and come back 401.
 */
const API_BASE_URLS = [
  environment.authenticationApiUrl,
  environment.documentWorkspaceApiUrl,
  environment.notificationApiUrl,
  environment.aiApiUrl,
];

function isApiRequest(url: string): boolean {
  // A leading slash covers the production setup, where every API sits behind
  // the same origin under its own path prefix.
  return url.startsWith('/') || API_BASE_URLS.some((base) => !!base && url.startsWith(base));
}

function withToken<T>(req: HttpRequest<T>, token: string): HttpRequest<T> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authenticationInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);
  const auth = inject(AuthenticationService);

  if (req.context.get(SKIP_AUTH) || !isApiRequest(req.url)) {
    return next(req);
  }

  const token = storage.accessToken;
  const authed = token ? withToken(req, token) : req;

  return next(authed).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && token) {
        return auth.refreshAccessToken().pipe(
          switchMap((fresh) => next(withToken(req, fresh))),
          catchError((refreshErr) => throwError(() => refreshErr)),
        ) as Observable<never>;
      }
      return throwError(() => error);
    }),
  );
};
