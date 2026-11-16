import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, Observable } from 'rxjs';
import { AccountDto, ProblemDetail } from '../models';
import { MOCK_DELAY_MS, mockApiInterceptor, resetMocks } from './mock-api.interceptor';

const API = '/api/v1';
const AUTH = { headers: { Authorization: 'Bearer any-token' } };
const SAVINGS = '00000000-0000-4000-d000-000000000002';

describe('mockApiInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    resetMocks();
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

  /** Skips the fake delay, returns the body or the error. */
  async function send<T>(request: Observable<T>) {
    const result = firstValueFrom(request).then(
      (body) => ({ body, error: undefined }),
      (error: HttpErrorResponse) => ({ body: undefined, error }),
    );
    await vi.advanceTimersByTimeAsync(MOCK_DELAY_MS);
    return result;
  }

  it.each([
    ['POST', '/auth/register'],
    ['POST', '/auth/login'],
    ['POST', '/auth/logout'],
    ['GET', '/me'],
    ['POST', '/transfers'],
    ['DELETE', '/accounts'],
  ])('sends %s %s to the real backend', (method, path) => {
    http.request(method, `${API}${path}`).subscribe();
    backend.expectOne({ method, url: `${API}${path}` }).flush(null);
  });

  it('answers a mocked endpoint after the delay, without the backend', async () => {
    const { body } = await send(http.get<AccountDto[]>(`${API}/accounts`, AUTH));
    expect(body).toHaveLength(3);
  });

  it('takes the id from the path', async () => {
    const { body } = await send(http.get<AccountDto>(`${API}/accounts/${SAVINGS}`, AUTH));
    expect(body?.name).toBe('Holiday savings');
  });

  it('reads query params from both `params` and the URL', async () => {
    const page = (url: string, params = {}) =>
      send(http.get<{ totalElements: number }>(url, { ...AUTH, params }));
    const fromParams = await page(`${API}/accounts/${SAVINGS}/history`, { type: 'INTEREST' });
    const fromUrl = await page(`${API}/accounts/${SAVINGS}/history?type=INTEREST`);
    expect(fromParams.body?.totalElements).toBe(3);
    expect(fromUrl.body?.totalElements).toBe(3);
  });

  it('gives 401 without a token, like the backend', async () => {
    const { error } = await send(http.get(`${API}/accounts`));
    expect(error?.status).toBe(401);
    expect((error?.error as ProblemDetail).code).toBe('UNAUTHENTICATED');
  });

  it('keeps changes between requests until reset', async () => {
    await send(http.patch(`${API}/accounts/${SAVINGS}`, { name: 'Car' }, AUTH));
    const { body } = await send(http.get<AccountDto>(`${API}/accounts/${SAVINGS}`, AUTH));
    expect(body?.name).toBe('Car');
  });
});
