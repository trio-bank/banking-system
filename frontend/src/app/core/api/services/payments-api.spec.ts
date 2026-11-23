import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PaymentsApi } from './payments-api';

const API = '/api/v1';
const KEY = '3f2b8c1e-0000-4000-8000-000000000001';
const REQUEST = { accountId: '7d1e4c22-5b3a-4a8e-b0f1-93c2d4e5f601', amount: '200.00' };

describe('PaymentsApi', () => {
  let api: PaymentsApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PaymentsApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it.each([
    ['T3 deposit', 'atmDeposit', '/atm/deposits'],
    ['T4 withdrawal', 'atmWithdrawal', '/atm/withdrawals'],
  ] as const)('%s posts the request with the Idempotency-Key header', (_, method, path) => {
    api[method](REQUEST, KEY).subscribe();
    const req = backend.expectOne({ method: 'POST', url: `${API}${path}` });
    expect(req.request.body).toEqual(REQUEST);
    expect(req.request.headers.get('Idempotency-Key')).toBe(KEY);
    req.flush({});
  });
});
