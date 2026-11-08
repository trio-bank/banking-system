import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { mockApiInterceptor } from './mock-api.interceptor';

const API = '/api/v1';

describe('mockApiInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([mockApiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([
    ['POST', '/auth/register'],
    ['POST', '/auth/login'],
    ['POST', '/auth/logout'],
    ['GET', '/me'],
  ])('sends %s %s to the real backend', (method, path) => {
    http.request(method, `${API}${path}`).subscribe();
    backend.expectOne({ method, url: `${API}${path}` }).flush(null);
  });

  it('sends not mocked endpoints to the real backend', () => {
    let body: unknown;
    http.get(`${API}/accounts`).subscribe((res) => (body = res));
    backend.expectOne(`${API}/accounts`).flush([]);
    expect(body).toEqual([]);
  });
});
