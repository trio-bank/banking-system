import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore, homeUrl } from './auth.store';

/** Only logged-in users (/app, /admin). Others go to /login. */
export const authGuard: CanActivateFn = () =>
  inject(AuthStore).isLoggedIn() || inject(Router).parseUrl('/login');

/** Only guests (/login, /register). A logged-in user goes to their home page. */
export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  return !store.isLoggedIn() || inject(Router).parseUrl(homeUrl(store.role()));
};
