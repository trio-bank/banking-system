import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('app routes', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    harness = await RouterTestingHarness.create();
  });

  const heading = () => harness.routeNativeElement?.querySelector('h1')?.textContent?.trim();
  const url = () => TestBed.inject(Router).url;

  it('redirects the root path to /login', async () => {
    await harness.navigateByUrl('/');
    expect(url()).toBe('/login');
    expect(heading()).toBe('Log in');
  });

  it('shows the register placeholder on /register', async () => {
    await harness.navigateByUrl('/register');
    expect(heading()).toBe('Register');
  });

  it('opens the customer shell and redirects /app to /app/dashboard', async () => {
    await harness.navigateByUrl('/app');
    expect(url()).toBe('/app/dashboard');
    expect(harness.routeNativeElement?.querySelector('app-dashboard-page')).not.toBeNull();
  });

  it('opens the admin shell and redirects /admin to /admin/customers', async () => {
    await harness.navigateByUrl('/admin');
    expect(url()).toBe('/admin/customers');
    expect(harness.routeNativeElement?.querySelector('app-customer-search-page')).not.toBeNull();
  });

  it('shows the not-found page for unknown paths', async () => {
    await harness.navigateByUrl('/does-not-exist');
    expect(heading()).toBe('Page not found');
  });
});
