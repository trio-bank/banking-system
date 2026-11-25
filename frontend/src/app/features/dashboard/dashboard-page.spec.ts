import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  AccountDto,
  AccountHistoryItemDto,
  LoginResponse,
  PageResponse,
  ProblemDetail,
} from '../../core/api/models';
import {
  accountEntries,
  MOCK_PRODUCTS,
  mockAccounts,
  resetMockBank,
} from '../../../testing/mock-bank';
import { MOCK_USERS } from '../../../testing/mock-data';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { AuthStore } from '../../core/auth/auth.store';
import { formatMoney } from '../../shared/pipes';
import { operationIcon } from '../history/operation-icon';
import { DashboardPage, recentOperations, totalBalance } from './dashboard-page';

resetMockBank();
// Personal, savings, blocked personal — in the order C1 returns them.
const [PERSONAL, SAVINGS, BLOCKED] = mockAccounts().map((account) => ({ ...account }));
const ACCOUNTS = [PERSONAL, BLOCKED, SAVINGS];
const [ANNA] = MOCK_USERS;

const page = (id: string, size: number): PageResponse<unknown> => {
  const all = accountEntries(id);
  return { content: all.slice(0, size), page: 0, size, totalElements: all.length, totalPages: 1 };
};

const problem = (code: string): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status: 0,
  detail: '',
  instance: '/api/v1/accounts',
  code,
  traceId: '',
  timestamp: '2026-11-16T12:00:00Z',
  errors: [],
});

describe('DashboardPage', () => {
  const list = vi.fn();
  const history = vi.fn();
  const products = vi.fn();

  const login = (lastLoginAt: string | null) => {
    const store = TestBed.inject(AuthStore);
    const response: LoginResponse = {
      accessToken: 't',
      tokenType: 'Bearer',
      expiresIn: 1800,
      expiresAt: '2099-01-01T00:00:00Z',
      user: { ...ANNA, lastLoginAt, customer: null },
    };
    store.setSession(response);
    store.setUser({ ...ANNA, lastLoginAt: '2026-11-16T10:00:00Z' }); // like GET /me after login
  };

  const open = async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/app/dashboard', DashboardPage);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return { harness, el: harness.routeNativeElement! };
  };

  beforeEach(() => {
    sessionStorage.clear();
    list.mockReset().mockReturnValue(of(ACCOUNTS));
    products.mockReset().mockReturnValue(of(MOCK_PRODUCTS));
    history
      .mockReset()
      .mockImplementation((id: string, query: { size: number }) => of(page(id, query.size)));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'app/dashboard', component: DashboardPage }]),
        { provide: AccountsApi, useValue: { list, history, products } },
      ],
    });
    login('2026-10-23T18:10:00Z');
  });

  it('greets by first name and shows the previous login, not the current one', async () => {
    const text = (await open()).el.textContent ?? '';

    expect(text).toContain('Hello, Anna');
    expect(text).toContain('Last login: 23.10.2026');
    expect(text).not.toContain('16.11.2026');
  });

  it('says it is the first login when there is no previous one', async () => {
    login(null);
    expect((await open()).el.textContent).toContain('This is your first login');
  });

  it('TC-18 shows a card per account: name, type, IBAN, balance, status', async () => {
    const { el } = await open();
    const cards = [...el.querySelectorAll<HTMLAnchorElement>('.card')];

    expect(cards).toHaveLength(3);
    const text = cards[0].textContent ?? '';
    expect(text).toContain('Trio Personal Account');
    expect(text).toContain('Personal');
    expect(text).toContain('PL45 9990 0012 1000 0000 0000 0001');
    expect(text).toContain(formatMoney(PERSONAL.balance));
    expect(text).toContain('Active');
    expect(cards[0].getAttribute('href')).toBe(`/app/accounts/${PERSONAL.id}`);
  });

  it('marks blocked accounts', async () => {
    const blocked = (await open()).el.querySelectorAll('.card')[1];

    expect(blocked.classList).toContain('inactive');
    expect(blocked.querySelector('.status')?.textContent).toContain('Blocked');
  });

  it('total counts only active accounts', async () => {
    const { el } = await open();

    // 10 420,51 + 3 324,97; the blocked 120,00 is left out
    expect(el.querySelector('[data-testid="total"]')?.textContent).toContain(
      formatMoney('13745.48'),
    );
  });

  it('shows the last 5 operations of all accounts, each row opens its account', async () => {
    const { el } = await open();
    const rows = el.querySelectorAll<HTMLAnchorElement>('a.operation');

    for (const account of ACCOUNTS) {
      expect(history).toHaveBeenCalledWith(account.id, { size: 5 });
    }
    expect(el.textContent).toContain('All accounts');
    expect(rows).toHaveLength(5);
    const meta = (row: Element) => row.querySelector('.op-meta')?.textContent?.replace(/\s+/g, ' ');
    expect(rows[1].textContent).toContain('Cinema');
    expect(rows[1].textContent).toContain(`−${formatMoney('64.00')}`);
    expect(meta(rows[1])).toContain(`13.11 · Ewa Wiśniewska · ${PERSONAL.name}`);
    expect(rows[1].getAttribute('href')).toBe(`/app/accounts/${PERSONAL.id}`);
    // Cash: the bank is not named, the title already says ATM.
    expect(rows[2].textContent).toContain('ATM cash withdrawal');
    expect(meta(rows[2])?.trim()).toBe(`10.11 · ${PERSONAL.name}`);
  });

  it('shows an own transfer once, as from → to, without a sign', async () => {
    const { el } = await open();
    const transfers = [...el.querySelectorAll('a.operation')].filter((row) =>
      row.textContent?.includes('Own transfer'),
    );

    expect(transfers).toHaveLength(1);
    const text = transfers[0].textContent ?? '';
    expect(text).toContain(`${PERSONAL.name} → ${SAVINGS.name}`);
    expect(text).toContain(formatMoney('800.00'));
    expect(text).not.toContain('−');
    expect(text).not.toContain('+');
    expect(transfers[0].getAttribute('href')).toBe(`/app/accounts/${PERSONAL.id}`);
  });

  it('shows card placeholders while the accounts load', async () => {
    list.mockReturnValue(new Subject<AccountDto[]>());
    const { el } = await open();

    expect(el.querySelector('[role="status"]')?.getAttribute('aria-label')).toBe(
      'Loading your accounts',
    );
    expect(el.querySelectorAll('.card-skeleton')).toHaveLength(3);
    expect(el.querySelector('[data-testid="total"]')).toBeNull();
  });

  it('shows row placeholders while the operations load', async () => {
    history.mockReturnValue(new Subject());
    const { el } = await open();

    expect(el.querySelectorAll('.card')).toHaveLength(3);
    expect(el.querySelector('[aria-label="Loading operations"]')).not.toBeNull();
  });

  it('puts "Open account" after the cards', async () => {
    const { el } = await open();

    const items = [...el.querySelectorAll('.cards > li')];
    expect(items).toHaveLength(4);
    expect(items[3].querySelector('a')?.getAttribute('href')).toBe('/app/accounts/new');
  });

  it('keeps "Open account" when there are no accounts', async () => {
    list.mockReturnValue(of([]));
    const { el } = await open();

    expect(el.querySelector('.card-add')?.getAttribute('href')).toBe('/app/accounts/new');
  });

  // 2 personal (1 blocked) + 2 savings: every product is at its limit of 2.
  const FULL = [...ACCOUNTS, { ...SAVINGS, id: 'savings-2' }];

  it('hides "Open account" when every product is at its limit', async () => {
    list.mockReturnValue(of(FULL));
    const { el } = await open();

    expect(el.querySelectorAll('app-account-card')).toHaveLength(4);
    expect(el.querySelector('.card-add')).toBeNull();
  });

  it('keeps "Open account" if the products fail to load: the backend checks anyway', async () => {
    list.mockReturnValue(of(FULL));
    products.mockReturnValue(throwError(() => problem('NETWORK_ERROR')));
    const { el } = await open();

    expect(el.querySelector('.card-add')).not.toBeNull();
    expect(el.querySelector('.banner')).toBeNull();
  });

  it('accounts error → banner, "Try again" loads again', async () => {
    list.mockReturnValueOnce(throwError(() => problem('NETWORK_ERROR')));
    const { harness, el } = await open();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Cannot reach the server');
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    harness.detectChanges();

    expect(list).toHaveBeenCalledTimes(2);
    expect(el.querySelectorAll('.card')).toHaveLength(3);
  });

  it('operations error keeps the accounts on screen', async () => {
    history.mockReturnValue(throwError(() => problem('INTERNAL_ERROR')));
    const { el } = await open();

    expect(el.querySelectorAll('.card')).toHaveLength(3);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong');
  });

  it('empty states: no accounts, no operations', async () => {
    list.mockReturnValue(of([]));
    expect((await open()).el.textContent).toContain('You have no accounts yet');
    expect(history).not.toHaveBeenCalled();
  });

  it('says so when no account has operations', async () => {
    history.mockReturnValue(of({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }));
    expect((await open()).el.textContent).toContain('No operations yet');
  });
});

describe('operationIcon', () => {
  it('picks an icon by type and falls back to the direction', () => {
    expect(operationIcon({ type: 'CASH_DEPOSIT', direction: 'INCOMING' })).toBe('local_atm');
    expect(operationIcon({ type: 'OWN_TRANSFER', direction: 'OUTGOING' })).toBe('sync_alt');
    expect(operationIcon({ type: 'NEW_TYPE', direction: 'INCOMING' })).toBe('south_west');
    expect(operationIcon({ type: 'NEW_TYPE', direction: 'OUTGOING' })).toBe('north_east');
  });
});

describe('totalBalance', () => {
  const account = (balance: string, status: AccountDto['status'] = 'ACTIVE') =>
    ({ ...PERSONAL, balance, status }) as AccountDto;

  it('adds without float errors', () => {
    expect(totalBalance([account('0.10'), account('0.20')])).toBe('0.30');
  });

  it('skips blocked and closed accounts', () => {
    expect(
      totalBalance([account('5.00'), account('7.00', 'BLOCKED'), account('9.00', 'CLOSED')]),
    ).toBe('5.00');
  });

  it('is 0.00 without accounts', () => {
    expect(totalBalance([])).toBe('0.00');
  });
});

describe('recentOperations', () => {
  let n = 0;
  const entry = (
    createdAt: string,
    changes: Partial<AccountHistoryItemDto> = {},
  ): AccountHistoryItemDto =>
    ({
      entryId: `e${++n}`,
      transactionId: `t${n}`,
      direction: 'INCOMING',
      counterpartyIban: null,
      createdAt: `2026-11-${createdAt}T10:00:00Z`,
      ...changes,
    }) as AccountHistoryItemDto;

  it('merges all accounts newest first and keeps the limit', () => {
    const rows = recentOperations(
      [PERSONAL, SAVINGS],
      [
        [entry('10'), entry('05')],
        [entry('12'), entry('01')],
      ],
      3,
    );

    expect(rows.map((r) => r.entry.createdAt.slice(8, 10))).toEqual(['12', '10', '05']);
    expect(rows.map((r) => r.account.id)).toEqual([SAVINGS.id, PERSONAL.id, PERSONAL.id]);
    expect(rows.every((r) => r.transfer === null)).toBe(true);
  });

  it('keeps an own transfer once and opens the account the money left', () => {
    const out = entry('14', {
      transactionId: 'own',
      direction: 'OUTGOING',
      counterpartyIban: SAVINGS.iban,
    });
    const into = { ...out, entryId: 'e-in', direction: 'INCOMING' as const };
    into.counterpartyIban = PERSONAL.iban;

    const rows = recentOperations([SAVINGS, PERSONAL], [[into], [out]], 5);

    expect(rows).toHaveLength(1);
    expect(rows[0].account.id).toBe(PERSONAL.id);
    expect(rows[0].transfer).toEqual({ from: PERSONAL, to: SAVINGS });
  });

  it('knows an own transfer even when only the incoming side was loaded', () => {
    const into = entry('14', { counterpartyIban: PERSONAL.iban });

    const [row] = recentOperations([PERSONAL, SAVINGS], [[], [into]], 5);

    expect(row.transfer).toEqual({ from: PERSONAL, to: SAVINGS });
    expect(row.account.id).toBe(PERSONAL.id);
  });

  it('a transfer to somebody else is not an own transfer', () => {
    const [row] = recentOperations(
      [PERSONAL],
      [[entry('14', { direction: 'OUTGOING', counterpartyIban: 'PL00OTHER' })]],
      5,
    );

    expect(row.transfer).toBeNull();
  });
});
