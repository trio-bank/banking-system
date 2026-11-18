import { Clipboard } from '@angular/cdk/clipboard';
import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { AccountDto, ProblemDetail } from '../../../core/api/models';
import { mockAccounts, resetMockBank } from '../../../core/api/mocks/mock-bank';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { formatMoney } from '../../../shared/pipes';
import { accountNameValidator, AccountDetailsPage, formatRate } from './account-details-page';

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
  const rename = vi.fn();
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
    rename.mockReset();
    rename.mockImplementation((id: string, { name }: { name: string }) =>
      of({ ...mockAccounts().find((a) => a.id === id)!, name }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'app/accounts/:accountId', component: AccountDetailsPage }],
          withComponentInputBinding(),
        ),
        { provide: AccountsApi, useValue: { get, rename } },
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

  describe('rename (US-09)', () => {
    const startRename = async (account = PERSONAL) => {
      const el = await open(account);
      el.querySelector<HTMLButtonElement>('[aria-label="Rename account"]')!.click();
      harness!.detectChanges();
      return el;
    };

    const type = (el: HTMLElement, value: string) => {
      const input = el.querySelector<HTMLInputElement>('.rename input')!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      harness!.detectChanges();
    };

    const submit = async (el: HTMLElement) => {
      el.querySelector<HTMLButtonElement>('.rename [type="submit"]')!.click();
      await harness!.fixture.whenStable();
      harness!.detectChanges();
    };

    it('opens the field with the current name', async () => {
      const el = await startRename();

      expect(el.querySelector<HTMLInputElement>('.rename input')!.value).toBe(PERSONAL.name);
      expect(el.querySelector('h1')).toBeNull();
    });

    it('TC-19 valid name: saves it trimmed and shows it in the header', async () => {
      const snack = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      const el = await startRename();

      type(el, '  Daily expenses  ');
      await submit(el);

      expect(rename).toHaveBeenCalledWith(PERSONAL.id, { name: 'Daily expenses' });
      expect(el.querySelector('.rename')).toBeNull();
      expect(el.querySelector('h1')?.textContent).toContain('Daily expenses');
      expect(snack).toHaveBeenCalledWith('Account renamed', undefined, { duration: 3000 });
    });

    it('TC-19 51 characters: error under the field, nothing sent', async () => {
      const el = await startRename();

      type(el, 'a'.repeat(51));
      await submit(el);

      expect(rename).not.toHaveBeenCalled();
      expect(el.querySelector('mat-error')?.textContent).toContain('At most 50 characters.');
    });

    it('accepts exactly 50 characters', async () => {
      const el = await startRename();

      type(el, 'a'.repeat(50));
      await submit(el);

      expect(rename).toHaveBeenCalledWith(PERSONAL.id, { name: 'a'.repeat(50) });
    });

    it('only spaces → "required", nothing sent', async () => {
      const el = await startRename();

      type(el, '   ');
      await submit(el);

      expect(rename).not.toHaveBeenCalled();
      expect(el.querySelector('mat-error')?.textContent).toContain('This field is required.');
    });

    it('same name → just closes, nothing sent', async () => {
      const el = await startRename();

      type(el, ` ${PERSONAL.name} `);
      await submit(el);

      expect(rename).not.toHaveBeenCalled();
      expect(el.querySelector('.rename')).toBeNull();
    });

    it('Cancel and Escape keep the old name', async () => {
      let el = await startRename();
      type(el, 'Something else');
      el.querySelector<HTMLButtonElement>('.rename [type="button"]')!.click();
      harness!.detectChanges();

      expect(el.querySelector('h1')?.textContent).toContain(PERSONAL.name);

      el = await startRename();
      type(el, 'Something else');
      el.querySelector('.rename')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      harness!.detectChanges();

      expect(rename).not.toHaveBeenCalled();
      expect(el.querySelector('h1')?.textContent).toContain(PERSONAL.name);
    });

    it('backend field error is shown under the field', async () => {
      rename.mockReturnValue(
        throwError(() => ({
          ...problem('VALIDATION_ERROR', 400),
          errors: [{ field: 'name', code: 'TOO_LONG', message: '' }],
        })),
      );
      const el = await startRename();

      type(el, 'New name');
      await submit(el);

      expect(el.querySelector('mat-error')?.textContent).toContain('Too long.');
      expect(el.querySelector('.rename')).not.toBeNull();
    });

    it('other error is shown under the field, and saving again works', async () => {
      rename.mockReturnValueOnce(throwError(() => problem('NETWORK_ERROR', 0)));
      const el = await startRename();

      type(el, 'New name');
      await submit(el);

      expect(el.querySelector('mat-error')?.textContent).toContain('Cannot reach the server');

      await submit(el);

      expect(rename).toHaveBeenCalledTimes(2);
      expect(el.querySelector('h1')?.textContent).toContain('New name');
    });

    it('blocks the buttons while saving', async () => {
      rename.mockReturnValue(new Subject<AccountDto>());
      const el = await startRename();

      type(el, 'New name');
      await submit(el);

      const buttons = el.querySelectorAll<HTMLButtonElement>('.rename button');
      expect([...buttons].every((b) => b.disabled)).toBe(true);
      expect(buttons[0].textContent).toContain('Saving…');
    });
  });
});

describe('accountNameValidator', () => {
  const check = (value: string) =>
    accountNameValidator(new FormControl(value, { nonNullable: true }));

  it.each(['A', 'Daily expenses', 'a'.repeat(50), `  ${'a'.repeat(50)}  `])(
    '"%s" is valid',
    (name) => {
      expect(check(name)).toBeNull();
    },
  );

  it('empty or only spaces → required', () => {
    expect(check('')).toEqual({ required: true });
    expect(check('   ')).toEqual({ required: true });
  });

  it('51 characters → maxlength', () => {
    expect(check('a'.repeat(51))).toEqual({ maxlength: { requiredLength: 50, actualLength: 51 } });
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
