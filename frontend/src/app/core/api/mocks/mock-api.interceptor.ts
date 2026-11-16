import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { of, switchMap, throwError, timer } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  accountHistory,
  getAccount,
  listAccounts,
  openAccount,
  products,
  renameAccount,
} from './accounts.mocks';
import { atmDeposit, atmWithdrawal, resetAtmMocks } from './atm.mocks';
import { resetMockBank } from './mock-bank';
import { MockHandler, unauthenticated } from './mock-http';

/** Fake network delay, so loading spinners are visible. */
export const MOCK_DELAY_MS = 300;

// One line per endpoint the backend doesn't have yet. `:id` matches any path part.
// Remove the line when the real endpoint is merged into develop (docs/05 §8).
const MOCKS: Record<string, MockHandler> = {
  'GET /account-products': products,
  'GET /accounts': listAccounts,
  'POST /accounts': openAccount,
  'GET /accounts/:id': getAccount,
  'PATCH /accounts/:id': renameAccount,
  'GET /accounts/:id/history': accountHistory,
  'POST /atm/deposits': atmDeposit,
  'POST /atm/withdrawals': atmWithdrawal,
};

/** Starts the mocked bank from the seed again. For tests. */
export function resetMocks(): void {
  resetMockBank();
  resetAtmMocks();
}

/**
 * Answers mocked endpoints without a backend. Everything else goes to the real API.
 * Enabled by `environment.useMocks`.
 */
export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  const url = req.url.split('?')[0];
  const path = url.startsWith(environment.apiUrl) ? url.slice(environment.apiUrl.length) : null;
  const match = path === null ? null : findMock(req.method, path);
  if (!match) {
    return next(req);
  }

  return timer(MOCK_DELAY_MS).pipe(
    switchMap(() => {
      // All mocked endpoints need a login. Which user it is doesn't matter (see mock-bank.ts).
      const result = req.headers.has('Authorization')
        ? match.handler(req, match.params)
        : unauthenticated(req);
      return result instanceof HttpErrorResponse ? throwError(() => result) : of(result);
    }),
  );
};

/** 'GET', '/accounts/7d1e/history' → the handler of 'GET /accounts/:id/history' + { id: '7d1e' }. */
function findMock(method: string, path: string) {
  const parts = path.split('/');
  for (const [route, handler] of Object.entries(MOCKS)) {
    const [routeMethod, routePath] = route.split(' ');
    const routeParts = routePath.split('/');
    if (routeMethod !== method || routeParts.length !== parts.length) {
      continue;
    }
    const params: Record<string, string> = {};
    const same = routeParts.every((part, i) => {
      if (part.startsWith(':')) {
        params[part.slice(1)] = decodeURIComponent(parts[i]);
        return parts[i] !== '';
      }
      return part === parts[i];
    });
    if (same) {
      return { handler, params };
    }
  }
  return null;
}
