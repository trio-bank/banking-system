import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

// Route map: docs/07-frontend-guide.md §3.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Log in · Trio Bank',
    loadComponent: () => import('./features/auth/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Register · Trio Bank',
    loadComponent: () =>
      import('./features/auth/register/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'register/success',
    canActivate: [guestGuard],
    title: 'Account created · Trio Bank',
    loadComponent: () =>
      import('./features/auth/register/register-success-page').then((m) => m.RegisterSuccessPage),
  },
  {
    path: 'app',
    canActivate: [authGuard, roleGuard('CUSTOMER')],
    loadComponent: () =>
      import('./layout/customer-shell/customer-shell').then((m) => m.CustomerShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · Trio Bank',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'profile',
        title: 'My profile · Trio Bank',
        loadComponent: () => import('./features/profile/profile-page').then((m) => m.ProfilePage),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./layout/admin-shell/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'customers' },
      {
        path: 'customers',
        title: 'Customers · Trio Bank',
        loadComponent: () =>
          import('./features/admin/customers/customer-search-page').then(
            (m) => m.CustomerSearchPage,
          ),
      },
    ],
  },
  {
    path: '**',
    title: 'Page not found · Trio Bank',
    loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
  },
];
