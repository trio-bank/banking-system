import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import {
  AccountHistoryItemDto,
  AccountHistoryQuery,
  PageResponse,
  ProblemDetail,
} from '../../../core/api/models';
import { accountEntries, mockAccounts, resetMockBank } from '../../../../testing/mock-bank';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { formatMoney } from '../../../shared/pipes';
import { AccountHistory, groupByDay, PHONE_QUERY } from './account-history';

resetMockBank();
const [PERSONAL, SAVINGS] = mockAccounts();
const PERSONAL_ENTRIES = accountEntries(PERSONAL.id); // 25, newest first

/** Pages like the backend: same slice the mock and C5 return. */
function pageOf(
  items: AccountHistoryItemDto[],
  page: number,
  size: number,
): PageResponse<AccountHistoryItemDto> {
  return {
    content: items.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

const problem = (code: string): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status: 500,
  detail: '',
  instance: '/api/v1/accounts/x/history',
  code,
  traceId: '',
  timestamp: '2026-11-18T12:00:00Z',
  errors: [],
});

describe('AccountHistory', () => {
  const history = vi.fn();
  // Desktop unless a test says otherwise.
  let screen: BehaviorSubject<BreakpointState>;
  const observe = vi.fn();
  const usePhone = (matches: boolean) => screen.next({ matches, breakpoints: {} });

  const render = async (accountId = PERSONAL.id) => {
    const fixture = TestBed.createComponent(AccountHistory);
    fixture.componentRef.setInput('accountId', accountId);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  };

  const rows = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('tr[mat-row]')];

  beforeEach(() => {
    history.mockReset();
    history.mockImplementation((id: string, { page = 0, size = 20 }: AccountHistoryQuery) =>
      of(pageOf(accountEntries(id), page, size)),
    );
    screen = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
    observe.mockReset();
    observe.mockReturnValue(screen);
    TestBed.configureTestingModule({
      providers: [
        { provide: AccountsApi, useValue: { history } },
        { provide: BreakpointObserver, useValue: { observe } },
      ],
    });
  });

  describe('phone (NFR-USE-01)', () => {
    const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('li.op')];

    it('watches the phone width', async () => {
      await render();

      expect(observe).toHaveBeenCalledWith(PHONE_QUERY);
    });

    it('shows one row per operation and no table', async () => {
      usePhone(true);
      const el = (await render()).nativeElement as HTMLElement;

      expect(el.querySelector('table')).toBeNull();
      expect(cards(el)).toHaveLength(20);
    });

    it('a row has icon, title, time · counterparty, signed amount and balance after', async () => {
      usePhone(true);
      const el = (await render()).nativeElement as HTMLElement;
      const first = PERSONAL_ENTRIES[0];
      const card = cards(el)[0];
      const text = (selector: string) =>
        card
          .querySelector(selector)
          ?.textContent?.replace(/[ \n]+/g, ' ')
          .trim();

      expect(card.querySelector('.op-icon mat-icon')).not.toBeNull();
      expect(text('.op-title')).toBe(first.title);
      expect(text('.op-meta')).toMatch(/^\d{2}:\d{2} · /);
      expect(text('.op-meta')).toContain(first.counterpartyName);
      expect(text('.amount')).toMatch(first.direction === 'INCOMING' ? /^\+/ : /^−/);
      expect(text('.balance-after')).toBe(
        `Balance after ${formatMoney(first.balanceAfter, first.currency)}`,
      );
    });

    it('groups the rows by day under a heading', async () => {
      usePhone(true);
      const el = (await render()).nativeElement as HTMLElement;
      const headings = el.querySelectorAll('h3.day');
      const groups = el.querySelectorAll('ul.ops');

      expect(headings.length).toBeGreaterThan(0);
      expect(groups).toHaveLength(headings.length);
    });

    it('incoming cards are marked, outgoing are not', async () => {
      usePhone(true);
      const el = (await render()).nativeElement as HTMLElement;
      const page = PERSONAL_ENTRIES.slice(0, 20);
      const amount = (i: number) => cards(el)[i].querySelector('.amount')!;

      expect(amount(page.findIndex((e) => e.direction === 'INCOMING')).classList).toContain(
        'incoming',
      );
      expect(amount(page.findIndex((e) => e.direction === 'OUTGOING')).classList).not.toContain(
        'incoming',
      );
    });

    it('the paginator works with cards too', async () => {
      usePhone(true);
      const fixture = await render();
      const el = fixture.nativeElement as HTMLElement;

      el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(cards(el)).toHaveLength(5);
    });

    it('switches between table and rows when the width changes', async () => {
      const fixture = await render();
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('table')).not.toBeNull();

      usePhone(true);
      fixture.detectChanges();
      expect(el.querySelector('table')).toBeNull();
      expect(cards(el)).toHaveLength(20);

      usePhone(false);
      fixture.detectChanges();
      expect(rows(el)).toHaveLength(20);
      expect(history).toHaveBeenCalledTimes(1); // no extra request
    });
  });

  it('TC-32 first page: 20 rows, newest first', async () => {
    const el = (await render()).nativeElement as HTMLElement;

    expect(history).toHaveBeenCalledWith(PERSONAL.id, { page: 0, size: 20 });
    expect(rows(el)).toHaveLength(20);
    expect(rows(el)[0].textContent).toContain(PERSONAL_ENTRIES[0].title);
  });

  it('shows date, description (title + counterparty), signed amount and balance after', async () => {
    const el = (await render()).nativeElement as HTMLElement;
    const headers = [...el.querySelectorAll('th')].map((th) => th.textContent?.trim());
    const first = PERSONAL_ENTRIES[0];
    const cells = [...rows(el)[0].querySelectorAll('td')].map((td) => td.textContent?.trim());

    expect(headers).toEqual(['Date', 'Description', 'Amount', 'Balance after']);
    expect(cells[0]).toMatch(/^\d{2}\.\d{2}\.\d{4}, \d{2}:\d{2}$/);
    expect(cells[1]).toContain(first.title);
    expect(cells[1]).toContain(first.counterpartyName);
    expect(cells[2]).toMatch(first.direction === 'INCOMING' ? /^\+/ : /^−/);
    expect(cells[3]).toBe(formatMoney(first.balanceAfter, first.currency));
  });

  it('incoming has "+" and the incoming color, outgoing has "−"', async () => {
    const el = (await render()).nativeElement as HTMLElement;
    const page = PERSONAL_ENTRIES.slice(0, 20);
    const inIndex = page.findIndex((e) => e.direction === 'INCOMING');
    const outIndex = page.findIndex((e) => e.direction === 'OUTGOING');
    const amount = (i: number) => rows(el)[i].querySelector<HTMLElement>('.amount')!;

    expect(amount(inIndex).textContent?.trim()).toBe(`+${formatMoney(page[inIndex].amount)}`);
    expect(amount(inIndex).classList).toContain('incoming');
    expect(amount(outIndex).textContent?.trim()).toBe(`−${formatMoney(page[outIndex].amount)}`);
    expect(amount(outIndex).classList).not.toContain('incoming');
  });

  it('shows the counterparty IBAN, but not for Trio Bank', async () => {
    const el = (await render()).nativeElement as HTMLElement;
    const page = PERSONAL_ENTRIES.slice(0, 20);
    const bank = page.findIndex((e) => e.counterpartyIban === null);
    const person = page.findIndex((e) => e.counterpartyIban !== null);

    expect(rows(el)[bank].querySelector('.iban')).toBeNull();
    expect(rows(el)[person].querySelector('.iban')?.textContent).toMatch(/^PL\d{2}( \d{4}){6}$/);
  });

  it('TC-32 paginator: next page asks for page 1 and shows the other 5', async () => {
    const fixture = await render();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.mat-mdc-paginator-range-label')?.textContent).toContain(
      '1 – 20 of 25',
    );

    el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(history).toHaveBeenLastCalledWith(PERSONAL.id, { page: 1, size: 20 });
    expect(rows(el)).toHaveLength(5);
    expect(rows(el)[0].textContent).toContain(PERSONAL_ENTRIES[20].title);
  });

  it('no paginator when everything fits on one page', async () => {
    history.mockReturnValue(of(pageOf(PERSONAL_ENTRIES.slice(0, 3), 0, 20)));
    const el = (await render()).nativeElement as HTMLElement;

    expect(rows(el)).toHaveLength(3);
    expect(el.querySelector('mat-paginator')).toBeNull();
  });

  it('empty state', async () => {
    history.mockReturnValue(of(pageOf([], 0, 20)));
    const el = (await render()).nativeElement as HTMLElement;

    expect(el.textContent).toContain('No operations yet.');
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows placeholders on the first load', async () => {
    history.mockReturnValue(new Subject());
    const el = (await render()).nativeElement as HTMLElement;

    expect(el.querySelector('[role="status"] .skeleton')).not.toBeNull();
  });

  it('keeps the old page dimmed while the next one loads', async () => {
    const fixture = await render();
    const el = fixture.nativeElement as HTMLElement;
    history.mockReturnValue(new Subject());

    el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
    fixture.detectChanges();

    expect(rows(el)).toHaveLength(20);
    expect(el.querySelector('.list')?.classList).toContain('busy');
    expect(el.querySelector('.skeleton')).toBeNull();
  });

  it('error → banner, "Try again" asks for the same page again', async () => {
    const fixture = await render();
    const el = fixture.nativeElement as HTMLElement;
    history.mockReturnValueOnce(throwError(() => problem('INTERNAL_ERROR')));

    el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Something went wrong');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(history).toHaveBeenLastCalledWith(PERSONAL.id, { page: 1, size: 20 });
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(rows(el)).toHaveLength(5);
  });

  it('another account → starts again from the first page', async () => {
    const fixture = await render();
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!
      .click();
    await fixture.whenStable();

    fixture.componentRef.setInput('accountId', SAVINGS.id);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(history).toHaveBeenLastCalledWith(SAVINGS.id, { page: 0, size: 20 });
  });
});

describe('groupByDay', () => {
  const entry = (createdAt: string) =>
    ({ ...PERSONAL_ENTRIES[0], entryId: createdAt, createdAt }) as AccountHistoryItemDto;
  const now = new Date(2026, 10, 21, 15, 0);

  it('"Today", then the date; keeps the order', () => {
    const days = groupByDay(
      [
        entry(new Date(2026, 10, 21, 14, 32).toISOString()),
        entry(new Date(2026, 10, 21, 9, 10).toISOString()),
        entry(new Date(2026, 10, 19, 18, 45).toISOString()),
      ],
      now,
      'en-US',
    );

    expect(days.map((d) => [d.label, d.items.length])).toEqual([
      ['Today', 2],
      ['19 November', 1],
    ]);
  });

  it('adds the year for another year', () => {
    const days = groupByDay([entry(new Date(2025, 2, 3, 12, 0).toISOString())], now, 'en-US');

    expect(days[0].label).toBe('3 March 2025');
  });

  it('nothing → no groups', () => {
    expect(groupByDay([], now, 'en-US')).toEqual([]);
  });
});
