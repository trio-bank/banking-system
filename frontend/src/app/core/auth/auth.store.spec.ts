import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LoginResponse } from '../api/models';
import { MOCK_USERS } from '../api/mocks/mock-data';
import { AuthStore, homeUrl } from './auth.store';

const [ANNA, ADMIN] = MOCK_USERS;

const loginResponse = (expiresAt = '2099-01-01T00:00:00Z'): LoginResponse => ({
  accessToken: 'token-123',
  tokenType: 'Bearer',
  expiresIn: 1800,
  expiresAt,
  user: { ...ANNA, customer: null },
});

describe('AuthStore', () => {
  let store: AuthStore;
  let backend: HttpTestingController;

  // A new store = like a page reload: it reads the token from sessionStorage again.
  function createStore() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(AuthStore);
    backend = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    sessionStorage.clear();
    createStore();
  });

  afterEach(() => backend.verify());

  it('starts logged out', () => {
    expect(store.isLoggedIn()).toBe(false);
    expect(store.token()).toBeNull();
    expect(store.role()).toBeNull();
  });

  it('keeps the session after login', () => {
    store.setSession(loginResponse());
    expect(store.isLoggedIn()).toBe(true);
    expect(store.token()).toBe('token-123');
    expect(store.role()).toBe('CUSTOMER');
    expect(sessionStorage.getItem('trio.session')).toContain('token-123');
  });

  it('forgets everything on clear', () => {
    store.setSession(loginResponse());
    store.clear();
    expect(store.isLoggedIn()).toBe(false);
    expect(sessionStorage.getItem('trio.session')).toBeNull();
  });

  describe('logout', () => {
    it('TC-11 tells the server and forgets the session', async () => {
      store.setSession(loginResponse());
      const loggingOut = store.logout();

      const req = backend.expectOne('/api/v1/auth/logout');
      expect(req.request.method).toBe('POST');
      expect(store.token()).toBe('token-123'); // still needed for this request

      req.flush(null, { status: 204, statusText: 'No Content' });
      await loggingOut;
      expect(store.isLoggedIn()).toBe(false);
      expect(sessionStorage.getItem('trio.session')).toBeNull();
    });

    it('forgets the session even when the server returns an error', async () => {
      store.setSession(loginResponse());
      const loggingOut = store.logout();
      backend.expectOne('/api/v1/auth/logout').flush(null, { status: 500, statusText: 'Error' });
      await loggingOut;
      expect(store.isLoggedIn()).toBe(false);
    });

    it('does not wait forever when the server does not answer', async () => {
      vi.useFakeTimers();
      try {
        store.setSession(loginResponse());
        const loggingOut = store.logout();
        backend.expectOne('/api/v1/auth/logout');

        await vi.advanceTimersByTimeAsync(5000);
        await loggingOut;
        expect(store.isLoggedIn()).toBe(false);
      } finally {
        vi.useRealTimers();
      }
    });
  });

  it('restores the user via GET /me after a reload', async () => {
    store.setSession(loginResponse());
    createStore();
    expect(store.isLoggedIn()).toBe(false); // token is there, user not loaded yet

    const restoring = store.restore();
    const req = backend.expectOne('/api/v1/me');
    req.flush(ADMIN);
    await restoring;

    expect(store.isLoggedIn()).toBe(true);
    expect(store.role()).toBe('ADMIN');
  });

  it('drops an expired token without calling the API', async () => {
    store.setSession(loginResponse('2020-01-01T00:00:00Z'));
    createStore();
    await store.restore();
    expect(store.token()).toBeNull();
  });

  it('clears the session when GET /me fails', async () => {
    store.setSession(loginResponse());
    createStore();
    const restoring = store.restore();
    backend.expectOne('/api/v1/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await restoring;
    expect(store.token()).toBeNull();
  });

  it('does nothing on start when there is no saved session', async () => {
    await store.restore();
    expect(store.isLoggedIn()).toBe(false);
  });

  it.each([
    ['CUSTOMER', '/app/dashboard'],
    ['ADMIN', '/admin/customers'],
    [null, '/login'],
  ] as const)('home page for %s is %s', (role, url) => {
    expect(homeUrl(role)).toBe(url);
  });
});
