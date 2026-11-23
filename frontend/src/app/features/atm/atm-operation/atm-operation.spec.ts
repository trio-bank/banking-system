import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AccountDto, ProblemDetail, TransactionDto } from '../../../core/api/models';
import { mockAccounts, resetMockBank } from '../../../../testing/mock-bank';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { PaymentsApi } from '../../../core/api/services/payments-api';
import { formatMoney } from '../../../shared/pipes';
import { AtmOperation } from './atm-operation';

// Intl puts non-breaking spaces into money, so expected texts are built the same way.
const LIMITS_TEXT = `From ${formatMoney('10')} to ${formatMoney('20000')}.`;

resetMockBank();
const [PERSONAL, , BLOCKED] = mockAccounts().map((account) => ({ ...account }));
const UUID = /^[0-9a-f-]{36}$/;

const problem = (code: string, status = 422): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status,
  detail: '',
  instance: '/api/v1/atm/deposits',
  code,
  traceId: '',
  timestamp: '2026-11-19T12:00:00Z',
  errors: [],
});

const DEPOSIT = {
  id: 'tx-1',
  reference: 'TB202611190000000042',
  type: 'CASH_DEPOSIT',
  amount: '200.00',
  currency: 'PLN',
  title: 'ATM cash deposit',
} as TransactionDto;

/** A personal account with little money, for "not above the balance" (BR-11). */
const LOW: AccountDto = { ...PERSONAL, id: 'low-account', name: 'Pocket money', balance: '120.00' };

const WITHDRAWAL = {
  ...DEPOSIT,
  id: 'tx-2',
  reference: 'TB202611200000000043',
  type: 'CASH_WITHDRAWAL',
  title: 'ATM cash withdrawal',
} as TransactionDto;

describe('AtmOperation', () => {
  const atmDeposit = vi.fn();
  const atmWithdrawal = vi.fn();
  const get = vi.fn();
  let fixture: ComponentFixture<AtmOperation>;
  let el: HTMLElement;
  let changed: AccountDto[];

  const render = async (
    kind: 'deposit' | 'withdrawal' = 'deposit',
    accounts: AccountDto[] = [PERSONAL, BLOCKED],
  ) => {
    fixture = TestBed.createComponent(AtmOperation);
    fixture.componentRef.setInput('kind', kind);
    fixture.componentRef.setInput('accounts', accounts);
    changed = [];
    fixture.componentInstance.accountChanged.subscribe((a) => changed.push(a));
    el = fixture.nativeElement;
    await settle();
  };
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const text = () => el.textContent ?? '';
  const button = (label: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) =>
      b.textContent?.includes(label),
    )!;
  const amountInput = () => el.querySelector<HTMLInputElement>('input[formcontrolname=amount]')!;
  const typeAmount = async (value: string) => {
    amountInput().value = value;
    amountInput().dispatchEvent(new Event('input'));
    await settle();
  };
  const press = async (label: string) => {
    button(label).click();
    await settle();
  };
  /** Form filled → confirmation. */
  const toConfirm = async (amount = '200') => {
    await typeAmount(amount);
    await press('Next');
  };

  beforeEach(() => {
    atmDeposit.mockReset();
    atmDeposit.mockReturnValue(of(DEPOSIT));
    atmWithdrawal.mockReset();
    atmWithdrawal.mockReturnValue(of(WITHDRAWAL));
    get.mockReset();
    get.mockReturnValue(of({ ...PERSONAL, balance: '10620.51' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PaymentsApi, useValue: { atmDeposit, atmWithdrawal } },
        { provide: AccountsApi, useValue: { get } },
      ],
    });
  });

  it('the first active account is chosen; blocked ones are in the list but disabled', async () => {
    await render();
    expect(el.querySelector('mat-select')?.textContent).toContain(PERSONAL.name);

    el.querySelector<HTMLElement>('.mat-mdc-select-trigger')!.click();
    await settle();
    const options = [...document.querySelectorAll('mat-option')];
    expect(options).toHaveLength(2);
    const blocked = options.find((o) => o.textContent?.includes(BLOCKED.name))!;
    expect(blocked.textContent).toContain('(blocked)');
    expect(blocked.getAttribute('aria-disabled')).toBe('true');
  });

  it('a quick amount fills the field and is marked as chosen', async () => {
    await render();
    await press('200 zł');

    expect(amountInput().value).toBe('200');
    expect(button('200 zł').getAttribute('aria-pressed')).toBe('true');
    expect(button('50 zł').getAttribute('aria-pressed')).toBe('false');
  });

  it.each([
    ['', 'Enter an amount.'],
    ['abc', 'Enter an amount, e.g. 200.'],
    ['15', 'Only whole tens: the ATM works with banknotes.'],
    ['10.50', 'Only whole tens: the ATM works with banknotes.'],
    ['5', LIMITS_TEXT],
    ['25000', LIMITS_TEXT],
  ])('TC-35 amount "%s" → "%s", nothing is sent', async (amount, message) => {
    await render();
    await toConfirm(amount);

    expect(el.querySelector('mat-error')?.textContent).toContain(message);
    expect(text()).not.toContain('Check and confirm');
    expect(atmDeposit).not.toHaveBeenCalled();
  });

  it.each(['10', '20 000', '1 250'])('amount "%s" is allowed', async (amount) => {
    await render();
    await toConfirm(amount);
    expect(text()).toContain('Check and confirm');
  });

  it('TC-34 deposit 200: confirmation → T3 with a key → result with the new balance', async () => {
    await render();
    await toConfirm('200');

    expect(text()).toContain('ATM cash deposit');
    expect(text()).toContain(PERSONAL.name);
    expect(text()).toContain('PL45 9990 0012 1000 0000 0000 0001');
    expect(text()).toContain(formatMoney('200'));
    expect(atmDeposit).not.toHaveBeenCalled(); // nothing is sent before Confirm

    await press('Confirm');

    expect(atmDeposit).toHaveBeenCalledWith(
      { accountId: PERSONAL.id, amount: '200.00' },
      expect.stringMatching(UUID),
    );
    expect(get).toHaveBeenCalledWith(PERSONAL.id);
    expect(text()).toContain('Cash deposited');
    expect(text()).toContain(`+${formatMoney('200')}`);
    expect(text()).toContain(formatMoney('10620.51'));
    expect(text()).toContain('TB202611190000000042');
    expect(changed).toEqual([{ ...PERSONAL, balance: '10620.51' }]);
  });

  it('after an error "Confirm" again sends the same key', async () => {
    await render();
    atmDeposit.mockReturnValueOnce(throwError(() => problem('NETWORK_ERROR', 0)));
    await toConfirm();
    await press('Confirm');

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Cannot reach the server');
    await press('Confirm');

    expect(atmDeposit).toHaveBeenCalledTimes(2);
    const [first, second] = atmDeposit.mock.calls.map(([, key]) => key as string);
    expect(second).toBe(first);
    expect(text()).toContain('Cash deposited');
  });

  it('Back and Next again make a new key', async () => {
    await render();
    atmDeposit.mockReturnValueOnce(throwError(() => problem('INTERNAL_ERROR', 500)));
    await toConfirm();
    await press('Confirm');
    await press('Back');
    await toConfirm('300');
    await press('Confirm');

    const [first, second] = atmDeposit.mock.calls.map(([, key]) => key as string);
    expect(second).not.toBe(first);
    expect(atmDeposit).toHaveBeenLastCalledWith(
      { accountId: PERSONAL.id, amount: '300.00' },
      second,
    );
  });

  it.each([
    ['CASH_AMOUNT_INVALID', 'This amount cannot be paid in or out in cash.'],
    ['OPERATION_NOT_ALLOWED_FOR_ACCOUNT', 'This operation is not allowed for this account.'],
    ['RECIPIENT_ACCOUNT_UNAVAILABLE', 'The recipient account cannot receive money right now.'],
    ['SOURCE_ACCOUNT_UNAVAILABLE', 'This account cannot send money right now.'],
  ])('%s from the server is shown on the confirmation', async (code, message) => {
    await render();
    atmDeposit.mockReturnValue(throwError(() => problem(code)));
    await toConfirm();
    await press('Confirm');

    expect(el.querySelector('[role=alert]')?.textContent).toContain(message);
    expect(button('Confirm').disabled).toBe(false);
    expect(changed).toEqual([]);
  });

  it('while sending, both buttons are disabled and a second click sends nothing', async () => {
    await render();
    atmDeposit.mockReturnValue(new Subject());
    await toConfirm();
    await press('Confirm');
    button('Depositing…').click();

    expect(atmDeposit).toHaveBeenCalledTimes(1);
    expect(button('Depositing…').disabled).toBe(true);
    expect(button('Back').disabled).toBe(true);
  });

  it('if reading the account fails after the deposit, the result is shown without balance', async () => {
    await render();
    get.mockReturnValue(throwError(() => problem('INTERNAL_ERROR', 500)));
    await toConfirm();
    await press('Confirm');

    expect(text()).toContain('Cash deposited');
    expect(text()).toContain('TB202611190000000042');
    expect(text()).not.toContain('New balance');
    expect(changed).toEqual([]);
  });

  it('"New deposit" goes back to an empty form with the same account', async () => {
    await render();
    await toConfirm();
    await press('Confirm');
    await press('New deposit');

    expect(amountInput().value).toBe('');
    expect(el.querySelector('mat-select')?.textContent).toContain(PERSONAL.name);
    expect(el.querySelector('mat-error')).toBeNull();
  });

  describe('balance preview', () => {
    const after = () => el.querySelector('[data-testid=balance-after]')?.textContent?.trim() ?? '';

    it('deposit: changes while typing, "—" for an empty or wrong amount', async () => {
      await render('deposit');
      expect(text()).toContain(`Balance now${formatMoney(PERSONAL.balance)}`);
      expect(after()).toBe('—');

      await typeAmount('200');
      expect(after()).toBe(formatMoney('10620.51'));

      await typeAmount('2000');
      expect(after()).toBe(formatMoney('12420.51'));

      await typeAmount('15');
      expect(after()).toBe('—');
    });

    it('withdrawal: the balance goes down; above the balance shows "—"', async () => {
      await render('withdrawal', [LOW, PERSONAL]);
      await typeAmount('100');
      expect(after()).toBe(formatMoney('20'));

      await typeAmount('130');
      expect(after()).toBe('—'); // the error under the field says why
    });

    it('is shown on the confirmation too', async () => {
      await render('withdrawal');
      await toConfirm('200');
      expect(text()).toContain(`Balance after${formatMoney('10220.51')}`);
    });
  });

  describe('withdrawal', () => {
    const chooseAccount = async (name: string) => {
      el.querySelector<HTMLElement>('.mat-mdc-select-trigger')!.click();
      await settle();
      [...document.querySelectorAll<HTMLElement>('mat-option')]
        .find((o) => o.textContent?.includes(name))!
        .click();
      await settle();
    };

    it.each([
      ['5010', `From ${formatMoney('10')} to ${formatMoney('5000')}.`],
      ['15', 'Only whole tens: the ATM works with banknotes.'],
    ])('TC-35 amount "%s" → "%s"', async (amount, message) => {
      await render('withdrawal');
      await toConfirm(amount);

      expect(el.querySelector('mat-error')?.textContent).toContain(message);
      expect(atmWithdrawal).not.toHaveBeenCalled();
    });

    it('5 000 is the most you can take at once', async () => {
      await render('withdrawal');
      await toConfirm('5000');
      expect(text()).toContain('Check and confirm');
    });

    it('TC-37 more than the balance is not sent; another account is checked again', async () => {
      await render('withdrawal', [LOW, PERSONAL]);
      await toConfirm('200');

      expect(el.querySelector('mat-error')?.textContent).toContain(
        `You have only ${formatMoney('120')}.`,
      );
      expect(atmWithdrawal).not.toHaveBeenCalled();

      await chooseAccount(PERSONAL.name);
      expect(el.querySelector('mat-error')).toBeNull();
      await press('Next');
      expect(text()).toContain('Check and confirm');
    });

    it('a deposit is not limited by the balance', async () => {
      await render('deposit', [LOW]);
      await toConfirm('500');
      expect(text()).toContain('Check and confirm');
    });

    it('withdraws 200: T4 with a key → "Cash withdrawn" with a minus and the new balance', async () => {
      get.mockReturnValue(of({ ...PERSONAL, balance: '10220.51' }));
      await render('withdrawal');
      await toConfirm('200');
      expect(text()).toContain('ATM cash withdrawal');

      await press('Confirm');

      expect(atmWithdrawal).toHaveBeenCalledWith(
        { accountId: PERSONAL.id, amount: '200.00' },
        expect.stringMatching(UUID),
      );
      expect(atmDeposit).not.toHaveBeenCalled();
      expect(text()).toContain('Cash withdrawn');
      const amount = el.querySelector('p.big')!;
      expect(amount.textContent).toContain(`−${formatMoney('200')}`);
      expect(amount.classList).not.toContain('incoming'); // outgoing: normal text color
      expect(text()).toContain(formatMoney('10220.51'));
      expect(text()).toContain('TB202611200000000043');
      expect(button('New withdrawal')).toBeDefined();
    });

    it.each([
      ['INSUFFICIENT_FUNDS', 'You do not have enough money on this account.'],
      ['OPERATION_NOT_ALLOWED_FOR_ACCOUNT', 'This operation is not allowed for this account.'],
    ])('%s from the server is shown on the confirmation', async (code, message) => {
      // E.g. the balance changed after the page was loaded: only the backend knows.
      atmWithdrawal.mockReturnValue(throwError(() => problem(code)));
      await render('withdrawal');
      await toConfirm('200');
      await press('Confirm');

      expect(el.querySelector('[role=alert]')?.textContent).toContain(message);
    });
  });
});
