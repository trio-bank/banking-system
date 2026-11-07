import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { CurrentUserDto } from './core/api/models';
import { MOCK_USERS } from './core/api/mocks/mock-data';
import { AuthStore } from './core/auth/auth.store';

const [CUSTOMER, ADMIN] = MOCK_USERS;

describe('app routes', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    harness = await RouterTestingHarness.create();
  });

  const heading = () => harness.routeNativeElement?.querySelector('h1')?.textContent?.trim();
  const url = () => TestBed.inject(Router).url;
  const store = () => TestBed.inject(AuthStore);

  const logInAs = (user: CurrentUserDto) =>
    store().setSession({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 1800,
      expiresAt: '2099-01-01T00:00:00Z',
      user,
    });

  /** Presses "Log out" and answers the request like the server does. */
  const logOutWithButton = async () => {
    const button = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find(
      (element) => element.textContent?.includes('Log out'),
    );
    button?.click();

    const request = TestBed.inject(HttpTestingController).expectOne((req) =>
      req.url.endsWith('/auth/logout'),
    );
    expect(request.request.method).toBe('POST');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();
  };

  describe('guest', () => {
    it('redirects the root path to /login', async () => {
      await harness.navigateByUrl('/');
      expect(url()).toBe('/login');
      expect(heading()).toBe('Log in');
    });

    it('shows the register form on /register', async () => {
      await harness.navigateByUrl('/register');
      expect(heading()).toBe('Register');
    });

    it('goes back to /register when /register/success has no result to show', async () => {
      await harness.navigateByUrl('/register/success');
      await harness.fixture.whenStable();
      expect(url()).toBe('/register');
    });

    it.each(['/app', '/admin'])('cannot open %s and goes to /login', async (path) => {
      await harness.navigateByUrl(path);
      expect(url()).toBe('/login');
    });

    it('shows the not-found page for unknown paths', async () => {
      await harness.navigateByUrl('/does-not-exist');
      expect(heading()).toBe('Page not found');
    });
  });

  describe('customer', () => {
    beforeEach(() => logInAs(CUSTOMER));

    it('opens the customer shell and redirects /app to /app/dashboard', async () => {
      await harness.navigateByUrl('/app');
      expect(url()).toBe('/app/dashboard');
      expect(harness.routeNativeElement?.querySelector('app-dashboard-page')).not.toBeNull();
    });

    it.each(['/admin', '/login', '/register'])('cannot open %s', async (path) => {
      await harness.navigateByUrl(path);
      expect(url()).toBe('/app/dashboard');
    });

    it('TC-11 cannot open /app after logout', async () => {
      store().clear();
      await harness.navigateByUrl('/app');
      expect(url()).toBe('/login');
    });

    it('TC-11 the button in the toolbar logs out and closes the protected pages', async () => {
      await harness.navigateByUrl('/app');
      await logOutWithButton();

      expect(store().isLoggedIn()).toBe(false);
      expect(url()).toBe('/login');

      await harness.navigateByUrl('/app');
      expect(url()).toBe('/login');
    });
  });

  describe('admin', () => {
    beforeEach(() => logInAs(ADMIN));

    it('opens the admin shell and redirects /admin to /admin/customers', async () => {
      await harness.navigateByUrl('/admin');
      expect(url()).toBe('/admin/customers');
      expect(harness.routeNativeElement?.querySelector('app-customer-search-page')).not.toBeNull();
    });

    it.each(['/app', '/login'])('cannot open %s', async (path) => {
      await harness.navigateByUrl(path);
      expect(url()).toBe('/admin/customers');
    });

    it('TC-11 the button in the toolbar logs out and closes the protected pages', async () => {
      await harness.navigateByUrl('/admin');
      await logOutWithButton();

      expect(store().isLoggedIn()).toBe(false);
      expect(url()).toBe('/login');

      await harness.navigateByUrl('/admin');
      expect(url()).toBe('/login');
    });
  });
});
