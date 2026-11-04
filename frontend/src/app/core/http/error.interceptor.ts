import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from '../api/models';
import { AuthStore } from '../auth/auth.store';

/**
 * Every failed request ends up as a `ProblemDetail`, so pages only check `error.code`.
 * 401 = session is gone → clear it and go to /login?reason=expired.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      const problem = toProblem(error, req.url);
      // Wrong password on login is also 401, but that's not an expired session.
      if (problem.status === 401 && !req.url.endsWith('/auth/login')) {
        store.clear();
        void router.navigate(['/login'], { queryParams: { reason: 'expired' } });
      }
      return throwError(() => problem);
    }),
  );
};

/** Backend errors already are ProblemDetail. For the rest (no network, proxy errors) we build one. */
export function toProblem(error: unknown, url = ''): ProblemDetail {
  const body: unknown = error instanceof HttpErrorResponse ? error.error : null;
  if (isProblem(body)) {
    return body;
  }
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  return {
    type: 'about:blank',
    title: status === 0 ? 'Network error' : 'Unexpected error',
    status,
    detail: error instanceof Error ? error.message : '',
    instance: url,
    code: status === 0 ? 'NETWORK_ERROR' : 'INTERNAL_ERROR',
    traceId: '',
    timestamp: new Date().toISOString(),
    errors: [],
  };
}

function isProblem(value: unknown): value is ProblemDetail {
  return (
    typeof value === 'object' && value !== null && typeof (value as ProblemDetail).code === 'string'
  );
}
