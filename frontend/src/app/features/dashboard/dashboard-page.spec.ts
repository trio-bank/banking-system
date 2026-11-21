import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { AccountDto, LoginResponse, PageResponse, ProblemDetail } from '../../core/api/models';
import { accountEntries, mockAccounts, resetMockBank } from '../../core/api/mocks/mock-bank';
import { MOCK_USERS } from '../../core/api/mocks/mock-data';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { AuthStore } from '../../core/auth/auth.store';
import { formatMoney } from '../../shared/pipes';
import { DashboardPage, operationIcon, totalBalance } from './dashboard-page';

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
    history
      .mockReset()
      .mockImplementation((id: string, query: { size: number }) => of(page(id, query.size)));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'app/dashboard', component: DashboardPage }]),
        { provide: AccountsApi, useValue: { list, history } },
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

  it('shows the last 5 operations of the first account', async () => {
    const { el } = await open();
    const rows = el.querySelectorAll('.operation');

    expect(history).toHaveBeenCalledWith(PERSONAL.id, { size: 5 });
    expect(rows).toHaveLength(5);
    expect(rows[0].textContent).toContain('Own transfer');
    expect(rows[0].textContent).toContain(`−${formatMoney('800.00')}`);
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

  it('puts "Open account" after the cards and links "Show all" to the first account', async () => {
    const { el } = await open();

    const items = [...el.querySelectorAll('.cards > li')];
    expect(items).toHaveLength(4);
    expect(items[3].querySelector('a')?.getAttribute('href')).toBe('/app/accounts/new');
    expect(el.querySelector('.more')?.getAttribute('href')).toBe(`/app/accounts/${PERSONAL.id}`);
  });

  it('keeps "Open account" when there are no accounts', async () => {
    list.mockReturnValue(of([]));
    const { el } = await open();

    expect(el.querySelector('.card-add')?.getAttribute('href')).toBe('/app/accounts/new');
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

  it('says so when the first account has no operations', async () => {
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
