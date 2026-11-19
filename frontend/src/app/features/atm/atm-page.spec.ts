import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AccountDto, ProblemDetail } from '../../core/api/models';
import { mockAccounts, resetMockBank } from '../../core/api/mocks/mock-bank';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { PaymentsApi } from '../../core/api/services/payments-api';
import { AtmOperation } from './atm-operation/atm-operation';
import { AtmPage } from './atm-page';

resetMockBank();
const [PERSONAL, SAVINGS, BLOCKED] = mockAccounts().map((account) => ({ ...account }));

const problem = (code: string, status: number): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status,
  detail: '',
  instance: '/api/v1/accounts',
  code,
  traceId: '',
  timestamp: '2026-11-19T12:00:00Z',
  errors: [],
});

describe('AtmPage', () => {
  const list = vi.fn();
  let fixture: ComponentFixture<AtmPage>;
  let el: HTMLElement;

  const render = async () => {
    fixture = TestBed.createComponent(AtmPage);
    el = fixture.nativeElement;
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const operation = () =>
    fixture.debugElement.query(By.directive(AtmOperation))?.componentInstance as
      AtmOperation | undefined;

  beforeEach(() => {
    list.mockReset();
    list.mockReturnValue(of([PERSONAL, SAVINGS, BLOCKED]));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AccountsApi, useValue: { list, get: vi.fn() } },
        { provide: PaymentsApi, useValue: { atmDeposit: vi.fn() } },
      ],
    });
  });

  it('US-14 shows the deposit with personal accounts only (BR-20)', async () => {
    await render();

    expect(el.querySelector('h1')?.textContent).toBe('ATM');
    expect(el.querySelector('h2')?.textContent).toBe('Deposit cash');
    expect(el.querySelector('[role="tab"]')).toBeNull();
    expect(operation()?.accounts()).toEqual([PERSONAL, BLOCKED]);
  });

  it('without an active personal account it says so and offers to open one', async () => {
    list.mockReturnValue(of([SAVINGS, BLOCKED]));
    await render();

    expect(operation()).toBeUndefined();
    expect(el.textContent).toContain('The ATM works only with an active personal account');
    expect(el.querySelector('a[href="/app/accounts/new"]')).not.toBeNull();
  });

  it('a new balance from the operation replaces the account in the list', async () => {
    await render();
    const updated: AccountDto = { ...PERSONAL, balance: '99.00' };
    operation()!.accountChanged.emit(updated);
    fixture.detectChanges();

    expect(operation()!.accounts()).toEqual([updated, BLOCKED]);
  });

  it('if the accounts fail to load, "Try again" loads them again', async () => {
    list.mockReturnValueOnce(throwError(() => problem('INTERNAL_ERROR', 500)));
    await render();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Something went wrong');

    el.querySelector<HTMLButtonElement>('.banner button')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(list).toHaveBeenCalledTimes(2);
    expect(operation()).toBeDefined();
  });
});
