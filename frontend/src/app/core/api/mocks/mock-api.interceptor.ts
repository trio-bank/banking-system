import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { of, switchMap, throwError, timer } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { MockHandler } from './mock-http';

/** Fake network delay, so loading spinners are visible. */
export const MOCK_DELAY_MS = 300;

// One line per endpoint the backend doesn't have yet, e.g. 'GET /accounts': accounts.
// Remove the line when the real endpoint is merged into develop (docs/05 §8).
const MOCKS: Record<string, MockHandler> = {};

/**
 * Answers mocked endpoints without a backend. Everything else goes to the real API.
 * Enabled by `environment.useMocks`.
 */
export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  const url = req.url.split('?')[0];
  const path = url.startsWith(environment.apiUrl) ? url.slice(environment.apiUrl.length) : null;
  const handler = path === null ? undefined : MOCKS[`${req.method} ${path}`];
  if (!handler) {
    return next(req);
  }

  return timer(MOCK_DELAY_MS).pipe(
    switchMap(() => {
      const result = handler(req);
      return result instanceof HttpErrorResponse ? throwError(() => result) : of(result);
    }),
  );
};
