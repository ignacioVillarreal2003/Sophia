import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { retry, timer } from 'rxjs';
import { RETRY_COUNT } from '@core/services/http-context';

const TRANSIENT = new Set([0, 502, 503, 504]);

export const retryInterceptor: HttpInterceptorFn = (req, next) => {
  const idempotent = req.method === 'GET' || req.method === 'HEAD';
  const count = req.context.get(RETRY_COUNT);

  if (!idempotent || count <= 0) {
    return next(req);
  }

  return next(req).pipe(
    retry({
      count,
      delay: (error, attempt) => {
        if (error instanceof HttpErrorResponse && TRANSIENT.has(error.status)) {
          return timer(Math.min(1000 * 2 ** (attempt - 1), 8000));
        }
        throw error;
      },
    }),
  );
};
