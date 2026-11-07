import {
  HttpClient,
  HttpErrorResponse,
  HttpResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, Observable } from 'rxjs';
import { CurrentUserDto, LoginResponse, ProblemDetail, RegistrationResponse } from '../models';
import { MOCK_DELAY_MS, mockApiInterceptor } from './mock-api.interceptor';
import { MOCK_PASSWORD } from './mock-data';

const API = '/api/v1';
const NOW = '2026-10-24T13:02:11Z';
const ANNA = 'anna.nowak@example.com';

describe('mockApiInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([mockApiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  /** Sends the request, skips the fake delay, returns the response or the error. */
  async function send<T>(
    request: Observable<HttpResponse<T>>,
  ): Promise<{ res?: HttpResponse<T>; error?: HttpErrorResponse }> {
    const result = firstValueFrom(request).then(
      (res) => ({ res }),
      (error: HttpErrorResponse) => ({ error }),
    );
    await vi.advanceTimersByTimeAsync(MOCK_DELAY_MS);
    return result;
  }

  const errorCode = (error?: HttpErrorResponse) => (error?.error as ProblemDetail).code;

  const login = (email: string, password = MOCK_PASSWORD) =>
    send(
      http.post<LoginResponse>(`${API}/auth/login`, { email, password }, { observe: 'response' }),
    );

  const withToken = (token: string) => ({
    observe: 'response' as const,
    headers: { Authorization: `Bearer ${token}` },
  });

  describe('A1 POST /auth/register', () => {
    const register = (email: string, pesel = '85110356782') =>
      send(
        http.post<RegistrationResponse>(
          `${API}/auth/register`,
          { email, password: MOCK_PASSWORD, pesel },
          { observe: 'response' },
        ),
      );

    it('TC-01 returns 201 with customer number and IBAN', async () => {
      const { res } = await register('new.user@example.com');
      expect(res?.status).toBe(201);
      expect(res?.headers.get('Location')).toBe('/api/v1/me');
      expect(res?.body?.email).toBe('new.user@example.com');
      expect(res?.body?.customerNumber).toBe('10000100');
      expect(res?.body?.account.iban).toBe('PL45999000121000000000000001');
    });

    it('TC-02 returns 409 EMAIL_ALREADY_REGISTERED for a taken email', async () => {
      const { error } = await register(' Anna.Nowak@example.com ');
      expect(error?.status).toBe(409);
      expect(errorCode(error)).toBe('EMAIL_ALREADY_REGISTERED');
    });

    it.each(['90051412343', '94011684627'])(
      'returns 409 PESEL_ALREADY_REGISTERED for the taken PESEL %s',
      async (pesel) => {
        const { error } = await register('new.user@example.com', pesel);
        expect(error?.status).toBe(409);
        expect(errorCode(error)).toBe('PESEL_ALREADY_REGISTERED');
      },
    );

    it('TC-04 returns 422 CUSTOMER_UNDERAGE for a person under 18', async () => {
      const { error } = await register('new.user@example.com', '10270111119');
      expect(error?.status).toBe(422);
      expect(errorCode(error)).toBe('CUSTOMER_UNDERAGE');
    });
  });

  describe('A2 POST /auth/login', () => {
    it('TC-07 returns 200 with a token valid for 30 minutes', async () => {
      const { res } = await login(ANNA);
      expect(res?.status).toBe(200);
      expect(res?.body?.accessToken).toBeTruthy();
      expect(res?.body?.expiresIn).toBe(1800);
      expect(res?.body?.expiresAt).toBe('2026-10-24T13:32:11Z');
      expect(res?.body?.user.role).toBe('CUSTOMER');
      expect(res?.body?.user.customer).toBeNull();
    });

    it.each([
      ['wrong password', ANNA, 'Wrong!Passw0rd'],
      ['unknown email', 'nobody@example.com', MOCK_PASSWORD],
    ])('TC-08 returns 401 INVALID_CREDENTIALS for %s', async (_, email, password) => {
      const { error } = await login(email, password);
      expect(error?.status).toBe(401);
      expect(errorCode(error)).toBe('INVALID_CREDENTIALS');
    });

    it('TC-09 returns 403 USER_LOCKED with lockedUntil for the locked user', async () => {
      const { error } = await login('locked@example.com');
      expect(error?.status).toBe(403);
      expect(error?.error).toMatchObject({
        code: 'USER_LOCKED',
        lockedUntil: '2026-10-24T13:17:11Z',
      });
    });

    it('TC-10 returns 403 USER_BLOCKED for the blocked user', async () => {
      const { error } = await login('blocked@example.com');
      expect(error?.status).toBe(403);
      expect(errorCode(error)).toBe('USER_BLOCKED');
    });
  });

  describe('A3 POST /auth/logout', () => {
    it('TC-11 returns 204 with a token', async () => {
      const token = (await login(ANNA)).res?.body?.accessToken ?? '';
      const { res } = await send(http.post(`${API}/auth/logout`, null, withToken(token)));
      expect(res?.status).toBe(204);
    });

    it('returns 401 without a token', async () => {
      const { error } = await send(http.post(`${API}/auth/logout`, null, { observe: 'response' }));
      expect(error?.status).toBe(401);
      expect(errorCode(error)).toBe('UNAUTHENTICATED');
    });
  });

  describe('A4 GET /me', () => {
    it('TC-13 returns the customer profile with masked PESEL', async () => {
      const token = (await login(ANNA)).res?.body?.accessToken ?? '';
      const { res } = await send(http.get<CurrentUserDto>(`${API}/me`, withToken(token)));
      expect(res?.body?.email).toBe(ANNA);
      expect(res?.body?.customer?.peselMasked).toBe('*******2343');
    });

    it('TC-13 returns customer = null for an admin', async () => {
      const token = (await login('admin@triobank.example')).res?.body?.accessToken ?? '';
      const { res } = await send(http.get<CurrentUserDto>(`${API}/me`, withToken(token)));
      expect(res?.body?.role).toBe('ADMIN');
      expect(res?.body?.customer).toBeNull();
    });

    it('returns 401 with an invalid token', async () => {
      const { error } = await send(http.get(`${API}/me`, withToken('fake')));
      expect(error?.status).toBe(401);
    });
  });

  it('sends not mocked endpoints to the real backend', () => {
    let body: unknown;
    http.get(`${API}/accounts`).subscribe((res) => (body = res));
    backend.expectOne(`${API}/accounts`).flush([]);
    expect(body).toEqual([]);
  });
});
