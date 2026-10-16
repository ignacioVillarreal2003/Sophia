import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AppError } from '@core/models/api.model';
import { ToastService } from '@core/services/toast.service';
import { SKIP_AUTH, SKIP_ERROR_TOAST } from '@core/services/http-context';

interface BackendError {
  message?: string;
  code?: string;
  requestId?: string;
  fieldErrors?: { field: string; message: string; rejectedValue?: unknown }[];
}

const RETRYABLE = new Set([0, 408, 429, 502, 503, 504]);

function normalise(error: HttpErrorResponse): AppError {
  const body = (error.error ?? {}) as BackendError;
  const fieldErrors = body.fieldErrors?.reduce<Record<string, string>>((acc, e) => {
    acc[e.field] = e.message;
    return acc;
  }, {});

  return {
    status: error.status,
    code: body.code ?? `HTTP_${error.status}`,
    message:
      body.message ??
      (error.status === 0
        ? 'No se pudo conectar con el servidor.'
        : error.message || 'Error inesperado.'),
    fieldErrors,
    requestId: body.requestId,
    retryable: RETRYABLE.has(error.status),
  };
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const appError = normalise(error);

      if (appError.status === 401 && !req.context.get(SKIP_AUTH)) {
        router.navigate(['/authentication/login'], { queryParams: { returnUrl: router.url } });
      } else if (!req.context.get(SKIP_ERROR_TOAST) && appError.status !== 422) {
        if (appError.status >= 500 || appError.status === 0 || appError.status === 403) {
          toast.error(appError.message);
        }
      }

      return throwError(() => appError);
    }),
  );
};
