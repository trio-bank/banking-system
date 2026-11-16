import { Clipboard } from '@angular/cdk/clipboard';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { AccountDto, ProblemDetail } from '../../../core/api/models';
import { mockAccounts, resetMockBank } from '../../../core/api/mocks/mock-bank';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { formatMoney } from '../../../shared/pipes';
import { AccountDetailsPage, formatRate } from './account-details-page';

resetMockBank();
const [PERSONAL, SAVINGS, BLOCKED] = mockAccounts().map((account) => ({ ...account }));

const problem = (code: string, status: number): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status,
  detail: '',
  instance: '/api/v1/accounts/x',
  code,
  traceId: '',
  timestamp: '2026-11-16T12:00:00Z',
  errors: [],
});

describe('AccountDetailsPage', () => {
  const get = vi.fn();
  let harness: RouterTestingHarness | undefined;

  const open = async (account: AccountDto | string) => {
    const id = typeof account === 'string' ? account : account.id;
    harness ??= await RouterTestingHarness.create();
    await harness.navigateByUrl(`/app/accounts/${id}`, AccountDetailsPage);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  };

  beforeEach(() => {
    get.mockReset();
    get.mockImplementation((id: string) => of(mockAccounts().find((a) => a.id === id)));
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'app/accounts/:accountId', component: AccountDetailsPage }],
          withComponentInputBinding(),
        ),
        { provide: AccountsApi, useValue: { get } },
      ],
    });
    harness = undefined;
  });

  it('TC-18 shows the header of a personal account, without interest', async () => {
    const text = (await open(PERSONAL)).textContent ?? '';

    expect(get).toHaveBeenCalledWith(PERSONAL.id);
    expect(text).toContain('Trio Personal Account');
    expect(text).toContain('PL45 9990 0012 1000 0000 0000 0001');
    expect(text).toContain('Trio Personal Account (Personal)');
    expect(text).toContain('Active');
    expect(text).toContain(formatMoney(PERSONAL.balance));
    expect(text).toContain('03.08.2026');
    expect(text).not.toContain('Interest rate');
    expect(text).not.toContain('Interest this month');
  });

  it('TC-18 shows the rate and the interest of this month for savings', async () => {
    const text = (await open(SAVINGS)).textContent ?? '';

    expect(text).toContain('Holiday savings');
    expect(text).toContain('4,00% per year');
    expect(text).toContain(formatMoney(SAVINGS.accruedInterest));
  });

  it('warns that a blocked account cannot move money', async () => {
    const el = await open(BLOCKED);

    expect(el.querySelector('.status')?.textContent).toContain('Blocked');
    expect(el.querySelector('[role="note"]')?.textContent).toContain('cannot send or receive');
  });

  it('copies the IBAN without spaces', async () => {
    const copy = vi.spyOn(TestBed.inject(Clipboard), 'copy').mockReturnValue(true);
    const snack = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    const el = await open(PERSONAL);

    el.querySelector<HTMLButtonElement>('[aria-label="Copy IBAN"]')!.click();

    expect(copy).toHaveBeenCalledWith('PL45999000121000000000000001');
    expect(snack).toHaveBeenCalledWith('IBAN copied', undefined, { duration: 3000 });
  });

  it('has an empty History tab for now', async () => {
    const el = await open(PERSONAL);

    expect(el.querySelector('[role="tab"]')?.textContent).toContain('History');
  });

  it('shows a loader while waiting', async () => {
    get.mockReturnValue(new Subject<AccountDto>());
    const el = await open(PERSONAL);

    expect(el.querySelector('mat-spinner')).not.toBeNull();
  });

  it('404 (unknown or not mine) → "Account not found"', async () => {
    get.mockReturnValue(throwError(() => problem('RESOURCE_NOT_FOUND', 404)));
    const el = await open('00000000-0000-4000-d000-999999999999');

    expect(el.querySelector('h1')?.textContent).toContain('Account not found');
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('other error → banner, and "Try again" loads again', async () => {
    get.mockReturnValueOnce(throwError(() => problem('NETWORK_ERROR', 0)));
    const el = await open(PERSONAL);

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Cannot reach the server');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    harness!.detectChanges();

    expect(get).toHaveBeenCalledTimes(2);
    expect(el.textContent).toContain('Trio Personal Account');
  });

  it('loads the new account when only the id in the URL changes', async () => {
    await open(PERSONAL);
    const el = await open(SAVINGS);

    expect(get).toHaveBeenLastCalledWith(SAVINGS.id);
    expect(el.querySelector('h1')?.textContent).toContain('Holiday savings');
  });
});

describe('formatRate', () => {
  it.each([
    ['4.0000', '4,00%'],
    ['0.0000', '0,00%'],
    ['3.1250', '3,125%'],
  ])('%s → %s', (rate, expected) => {
    expect(formatRate(rate)).toBe(expected);
  });
});
