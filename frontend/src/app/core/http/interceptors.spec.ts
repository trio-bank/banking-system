import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ProblemDetail } from '../api/models';
import { MOCK_USERS } from '../api/mocks/mock-data';
import { AuthStore } from '../auth/auth.store';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';

const API = '/api/v1';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let store: AuthStore;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => backend.verify());

  const logIn = () =>
    store.setSession({
      accessToken: 'token-123',
      tokenType: 'Bearer',
      expiresIn: 1800,
      expiresAt: '2099-01-01T00:00:00Z',
      user: MOCK_USERS[0],
    });

  /** Sends GET and returns what the page would get in `error`. */
  function failWith(url: string, body: object | string, status: number): ProblemDetail | undefined {
    let problem: ProblemDetail | undefined;
    http.get(url).subscribe({ error: (e: ProblemDetail) => (problem = e) });
    backend.expectOne(url).flush(body, { status, statusText: 'Error' });
    return problem;
  }

  describe('authInterceptor', () => {
    it('adds the Bearer token when logged in', () => {
      logIn();
      http.get(`${API}/me`).subscribe();
      const req = backend.expectOne(`${API}/me`);
      expect(req.request.headers.get('Authorization')).toBe('Bearer token-123');
      req.flush({});
    });

    it('sends no token when logged out', () => {
      http.get(`${API}/me`).subscribe();
      const req = backend.expectOne(`${API}/me`);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });

    it('never sends the token to other sites', () => {
      logIn();
      http.get('https://example.com/data').subscribe();
      const req = backend.expectOne('https://example.com/data');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('errorInterceptor', () => {
    it('passes the backend ProblemDetail to the page', () => {
      const body = { status: 422, code: 'INSUFFICIENT_FUNDS', errors: [] };
      const problem = failWith(`${API}/transfers`, body, 422);
      expect(problem?.code).toBe('INSUFFICIENT_FUNDS');
    });

    it('turns a network error into NETWORK_ERROR', () => {
      let problem: ProblemDetail | undefined;
      http.get(`${API}/me`).subscribe({ error: (e: ProblemDetail) => (problem = e) });
      backend.expectOne(`${API}/me`).error(new ProgressEvent('error'));
      expect(problem?.status).toBe(0);
      expect(problem?.code).toBe('NETWORK_ERROR');
    });

    it('turns a non-JSON server error into INTERNAL_ERROR', () => {
      const problem = failWith(`${API}/me`, '<html>Bad gateway</html>', 502);
      expect(problem?.code).toBe('INTERNAL_ERROR');
    });

    it('TC-12 on 401 clears the session and goes to /login?reason=expired', () => {
      logIn();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      failWith(`${API}/me`, { status: 401, code: 'UNAUTHENTICATED' }, 401);
      expect(store.isLoggedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
    });

    it('does not treat a wrong password on login as an expired session', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
      let problem: ProblemDetail | undefined;
      http.post(`${API}/auth/login`, {}).subscribe({ error: (e: ProblemDetail) => (problem = e) });
      backend
        .expectOne(`${API}/auth/login`)
        .flush({ status: 401, code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Error' });
      expect(problem?.code).toBe('INVALID_CREDENTIALS');
      expect(navigate).not.toHaveBeenCalled();
    });
  });
});
