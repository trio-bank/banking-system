import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { checkIban } from '../../../shared/validators';
import {
  AccountDto,
  AccountHistoryItemDto,
  AccountProductDto,
  PageResponse,
  ProblemDetail,
} from '../models';
import {
  accountHistory,
  getAccount,
  listAccounts,
  openAccount,
  products,
  renameAccount,
} from './accounts.mocks';
import { accountEntries, mockAccounts, resetMockBank } from './mock-bank';
import { MockResult } from './mock-http';

const API = '/api/v1';
const PERSONAL = '00000000-0000-4000-d000-000000000001';
const SAVINGS = '00000000-0000-4000-d000-000000000002';
const UNKNOWN = '00000000-0000-4000-d000-999999999999';

const get = (url: string) => new HttpRequest('GET', `${API}${url}`);
const post = (url: string, body: unknown) => new HttpRequest('POST', `${API}${url}`, body);
const patch = (url: string, body: unknown) => new HttpRequest('PATCH', `${API}${url}`, body);

function body<T>(result: MockResult): T {
  expect(result).toBeInstanceOf(HttpResponse);
  return (result as HttpResponse<T>).body as T;
}
const status = (result: MockResult) => result.status;
const code = (result: MockResult) =>
  ((result as HttpErrorResponse).error as ProblemDetail | undefined)?.code;
const fieldCode = (result: MockResult) =>
  ((result as HttpErrorResponse).error as ProblemDetail).errors[0]?.code;

describe('accounts mocks', () => {
  beforeEach(() => resetMockBank());

  describe('seed', () => {
    it('matches the IBAN examples in docs/03 §8.1, and every IBAN is valid', () => {
      const [personal, savings] = mockAccounts();
      expect(personal.iban).toBe('PL45999000121000000000000001');
      expect(savings.iban).toBe('PL18999000121000000000000002');
      for (const account of mockAccounts()) {
        expect(checkIban(account.iban)).toBeNull();
      }
    });

    it.each(mockAccounts().map((account) => [account.name, account.id]))(
      '%s: balance = the newest balanceAfter, never below zero',
      (_, id) => {
        const account = mockAccounts().find((a) => a.id === id)!;
        const entries = accountEntries(id);
        expect(entries[0].balanceAfter).toBe(account.balance);
        expect(entries.every((entry) => Number(entry.balanceAfter) >= 0)).toBe(true);
      },
    );

    it('own transfer appears on both accounts with the same transaction', () => {
      const out = accountEntries(PERSONAL).find((e) => e.type === 'OWN_TRANSFER')!;
      const incoming = accountEntries(SAVINGS).find((e) => e.transactionId === out.transactionId);
      expect(incoming?.direction).toBe(out.direction === 'OUTGOING' ? 'INCOMING' : 'OUTGOING');
      expect(incoming?.amount).toBe(out.amount);
    });
  });

  it('P1 returns both products', () => {
    const list = body<AccountProductDto[]>(products(get('/account-products'), {}));
    expect(list.map((p) => p.code)).toEqual(['PERSONAL_STANDARD', 'SAVINGS_STANDARD']);
  });

  it('C1 puts personal accounts first, then sorts by opening date', () => {
    const list = body<AccountDto[]>(listAccounts(get('/accounts'), {}));
    expect(list.map((a) => a.name)).toEqual([
      'Trio Personal Account',
      'Old personal account',
      'Holiday savings',
    ]);
    expect(list[1].status).toBe('BLOCKED');
  });

  describe('C2 open account', () => {
    it('201 with Location; the new account is in the list', () => {
      const result = openAccount(
        post('/accounts', { productCode: 'SAVINGS_STANDARD', name: ' Car ' }),
        {},
      );
      const account = body<AccountDto>(result);
      expect(status(result)).toBe(201);
      expect((result as HttpResponse<unknown>).headers.get('Location')).toBe(
        `/api/v1/accounts/${account.id}`,
      );
      expect(account).toMatchObject({
        name: 'Car',
        balance: '0.00',
        status: 'ACTIVE',
        accruedInterest: '0.000000',
      });
      expect(checkIban(account.iban)).toBeNull();
      expect(body<AccountDto[]>(listAccounts(get('/accounts'), {}))).toHaveLength(4);
    });

    it('uses the product name when the name is left out', () => {
      const account = body<AccountDto>(
        openAccount(post('/accounts', { productCode: 'SAVINGS_STANDARD' }), {}),
      );
      expect(account.name).toBe('Trio Savings Account');
    });

    it('a 3rd personal account → 422 ACCOUNT_LIMIT_REACHED', () => {
      const result = openAccount(post('/accounts', { productCode: 'PERSONAL_STANDARD' }), {});
      expect([status(result), code(result)]).toEqual([422, 'ACCOUNT_LIMIT_REACHED']);
    });

    it('unknown product → 404, too long name → 400', () => {
      expect(code(openAccount(post('/accounts', { productCode: 'GOLD' }), {}))).toBe(
        'RESOURCE_NOT_FOUND',
      );
      const long = openAccount(
        post('/accounts', { productCode: 'SAVINGS_STANDARD', name: 'x'.repeat(51) }),
        {},
      );
      expect([code(long), fieldCode(long)]).toEqual(['VALIDATION_ERROR', 'TOO_LONG']);
    });
  });

  it('C3 returns the account, unknown id → 404', () => {
    expect(body<AccountDto>(getAccount(get(`/accounts/${SAVINGS}`), { id: SAVINGS })).type).toBe(
      'SAVINGS',
    );
    expect(status(getAccount(get(`/accounts/${UNKNOWN}`), { id: UNKNOWN }))).toBe(404);
  });

  describe('C4 rename', () => {
    it('saves the trimmed name', () => {
      const renamed = body<AccountDto>(
        renameAccount(patch(`/accounts/${SAVINGS}`, { name: '  Car  ' }), { id: SAVINGS }),
      );
      expect(renamed.name).toBe('Car');
      expect(body<AccountDto>(getAccount(get(`/accounts/${SAVINGS}`), { id: SAVINGS })).name).toBe(
        'Car',
      );
    });

    it.each([
      ['   ', 'REQUIRED'],
      ['x'.repeat(51), 'TOO_LONG'],
    ])('%j → 400 %s', (name, fieldError) => {
      const result = renameAccount(patch(`/accounts/${SAVINGS}`, { name }), { id: SAVINGS });
      expect([status(result), fieldCode(result)]).toEqual([400, fieldError]);
    });
  });

  describe('C5 history', () => {
    const history = (query = '', id = PERSONAL) =>
      accountHistory(get(`/accounts/${id}/history${query}`), { id });
    const page = (query = '') => body<PageResponse<AccountHistoryItemDto>>(history(query));

    it('gives 20 newest of 25 on the first page', () => {
      const first = page();
      expect(first).toMatchObject({ page: 0, size: 20, totalElements: 25, totalPages: 2 });
      expect(first.content).toHaveLength(20);
      expect(first.content[0].title).toBe('Own transfer');
      expect(page('?page=1').content).toHaveLength(5);
    });

    it('filters by direction, type and Warsaw dates', () => {
      expect(page('?direction=INCOMING').content.every((e) => e.direction === 'INCOMING')).toBe(
        true,
      );
      const cash = page('?type=CASH_DEPOSIT&type=CASH_WITHDRAWAL');
      expect(cash.content.every((e) => e.channel === 'ATM')).toBe(true);
      expect(cash.totalElements).toBe(7);
      expect(page('?from=2026-11-01&to=2026-11-30').totalElements).toBe(5);
    });

    it('shows Trio Bank without IBAN for cash', () => {
      const deposit = page('?type=CASH_DEPOSIT').content[0];
      expect([deposit.counterpartyName, deposit.counterpartyIban]).toEqual(['Trio Bank', null]);
    });

    it('rejects from > to and size > 100, unknown account → 404', () => {
      expect(fieldCode(history('?from=2026-11-02&to=2026-11-01'))).toBe('INVALID_DATE');
      expect(fieldCode(history('?size=101'))).toBe('OUT_OF_RANGE');
      expect(status(history('', UNKNOWN))).toBe(404);
    });
  });
});
