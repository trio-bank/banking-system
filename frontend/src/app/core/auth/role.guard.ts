import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../api/models';
import { AuthStore, homeUrl } from './auth.store';

/** Only users with this role. E.g. a customer opening /admin goes back to /app/dashboard. */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const store = inject(AuthStore);
    return store.role() === role || inject(Router).parseUrl(homeUrl(store.role()));
  };
}
