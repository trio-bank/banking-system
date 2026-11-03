import { Routes } from '@angular/router';

// Route map: docs/07-frontend-guide.md §3. Guards (guest / auth + role) are added in the Core task.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    title: 'Log in · Trio Bank',
    loadComponent: () => import('./features/auth/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Register · Trio Bank',
    loadComponent: () =>
      import('./features/auth/register/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'app',
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
    ],
  },
  {
    path: 'admin',
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
