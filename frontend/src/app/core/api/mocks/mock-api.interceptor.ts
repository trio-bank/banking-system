import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { from, of, switchMap, throwError, timer } from 'rxjs';
import { environment } from '../../../../environments/environment';

/** Fake network delay, so loading spinners are visible. */
export const MOCK_DELAY_MS = 300;

/**
 * Answers mocked endpoints without a backend. Everything else goes to the real API.
 * Enabled by `environment.useMocks`. The list of mocks is in mock-routes.ts.
 */
export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  const url = req.url.split('?')[0];
  if (!url.startsWith(environment.apiUrl)) {
    return next(req);
  }
  const path = url.slice(environment.apiUrl.length);

  // `import()` = load the mocks only on the first API request, not with the app.
  // The browser keeps the loaded file, so later requests don't wait for it.
  return from(import('./mock-routes')).pipe(
    switchMap(({ findMock }) => {
      const answer = findMock(req.method, path);
      if (!answer) {
        return next(req);
      }
      return timer(MOCK_DELAY_MS).pipe(
        switchMap(() => {
          const result = answer(req);
          return result instanceof HttpErrorResponse ? throwError(() => result) : of(result);
        }),
      );
    }),
  );
};
