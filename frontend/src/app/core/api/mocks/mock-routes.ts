import { HttpRequest } from '@angular/common/http';
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
import { MockHandler, MockResult, unauthenticated } from './mock-http';

// Loaded lazily by the mock interceptor, so this file and the mock data are not in the
// first-load bundle.

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
 * 'GET', '/accounts/7d1e/history' → a function that answers it like
 * 'GET /accounts/:id/history' with { id: '7d1e' }. `null` = not mocked.
 */
export function findMock(
  method: string,
  path: string,
): ((req: HttpRequest<unknown>) => MockResult) | null {
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
      // All mocked endpoints need a login. Which user it is doesn't matter (see mock-bank.ts).
      return (req) =>
        req.headers.has('Authorization') ? handler(req, params) : unauthenticated(req);
    }
  }
  return null;
}
