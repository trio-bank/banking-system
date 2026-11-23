import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountsApi } from './accounts-api';

const API = '/api/v1';
const ID = '7d1e4c22-5b3a-4a8e-b0f1-93c2d4e5f601';

describe('AccountsApi', () => {
  let api: AccountsApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AccountsApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('P1 gets the product catalog', () => {
    let body: unknown;
    api.products().subscribe((res) => (body = res));
    backend.expectOne({ method: 'GET', url: `${API}/account-products` }).flush([]);
    expect(body).toEqual([]);
  });

  it('C1 gets my accounts', () => {
    api.list().subscribe();
    backend.expectOne({ method: 'GET', url: `${API}/accounts` }).flush([]);
  });

  it('C2 opens an account with the request as body', () => {
    api.open({ productCode: 'SAVINGS_STANDARD', name: 'Holiday' }).subscribe();
    const req = backend.expectOne({ method: 'POST', url: `${API}/accounts` });
    expect(req.request.body).toEqual({ productCode: 'SAVINGS_STANDARD', name: 'Holiday' });
    req.flush({});
  });

  it('C3 gets one account', () => {
    api.get(ID).subscribe();
    backend.expectOne({ method: 'GET', url: `${API}/accounts/${ID}` }).flush({});
  });

  it('C4 renames with PATCH', () => {
    api.rename(ID, { name: 'Daily expenses' }).subscribe();
    const req = backend.expectOne({ method: 'PATCH', url: `${API}/accounts/${ID}` });
    expect(req.request.body).toEqual({ name: 'Daily expenses' });
    req.flush({});
  });

  describe('C5 history', () => {
    it('sends no params when there are no filters', () => {
      api.history(ID).subscribe();
      const req = backend.expectOne(`${API}/accounts/${ID}/history`);
      expect(req.request.params.keys()).toEqual([]);
      req.flush({});
    });

    it('sends page and size, and skips the ones not set', () => {
      api.history(ID, { page: 2, size: undefined }).subscribe();
      const req = backend.expectOne((r) => r.url === `${API}/accounts/${ID}/history`);
      expect(req.request.urlWithParams).toBe(`${API}/accounts/${ID}/history?page=2`);
      req.flush({});
    });
  });
});
