import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AccountDto, FieldError, ProblemDetail } from '../../../core/api/models';
import { MOCK_PRODUCTS } from '../../../../testing/mock-bank';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { OpenAccountPage, productRules } from './open-account-page';

const [PERSONAL, SAVINGS] = MOCK_PRODUCTS;

const problem = (code: string, status: number, errors: FieldError[] = []): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status,
  detail: '',
  instance: '/api/v1/accounts',
  code,
  traceId: '',
  timestamp: '2026-11-19T12:00:00Z',
  errors,
});

const NEW_ACCOUNT = {
  id: 'new-account-id',
  iban: 'PL00999000121000000000000009',
  balance: '0.00',
} as AccountDto;

describe('OpenAccountPage', () => {
  const products = vi.fn();
  const open = vi.fn();
  const snackOpen = vi.fn();
  let fixture: ComponentFixture<OpenAccountPage>;
  let el: HTMLElement;

  const render = async () => {
    fixture = TestBed.createComponent(OpenAccountPage);
    el = fixture.nativeElement;
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const text = () => el.textContent ?? '';
  // The radio's [value] goes to Angular's radio directive, not to the DOM, so find it by card text.
  const radio = (code: string) => {
    const name = MOCK_PRODUCTS.find((p) => p.code === code)!.name;
    const card = [...el.querySelectorAll('.product')].find((c) => c.textContent?.includes(name));
    return card!.querySelector<HTMLInputElement>('input[type=radio]')!;
  };
  const nameInput = () => el.querySelector<HTMLInputElement>('input[formcontrolname=name]')!;
  const submit = () => el.querySelector<HTMLButtonElement>('button[type=submit]')!;

  const choose = (code: string) => {
    radio(code).click();
    fixture.detectChanges();
  };
  const typeName = (value: string) => {
    nameInput().value = value;
    nameInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const press = async () => {
    submit().click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(() => {
    products.mockReset();
    products.mockReturnValue(of(MOCK_PRODUCTS));
    open.mockReset();
    open.mockReturnValue(of(NEW_ACCOUNT));
    snackOpen.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AccountsApi, useValue: { products, open } },
        { provide: MatSnackBar, useValue: { open: snackOpen } },
      ],
    });
  });

  it('US-07 shows a card per product with its name, type and main rules', async () => {
    await render();

    expect(products).toHaveBeenCalledTimes(1);
    expect(el.querySelectorAll('.product')).toHaveLength(2);
    expect(text()).toContain('Trio Personal Account');
    expect(text()).toContain('Trio Savings Account');
    expect(text()).toContain('4,00% interest per year');
    expect(text()).toContain('Transfers only to your own accounts');
    expect(text()).toContain('Needs an active personal account');
    expect(submit().textContent).toContain('Open account');
  });

  it('the placeholder of the name is the chosen product name', async () => {
    await render();
    choose(SAVINGS.code);

    expect(nameInput().placeholder).toBe('Trio Savings Account');
    expect(radio(SAVINGS.code).closest('.product')!.classList).toContain('selected');
  });

  it('TC-15 opens a savings account without a name and goes to its details', async () => {
    await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    choose(SAVINGS.code);
    await press();

    // No "name" at all, so the backend sets the product name.
    expect(open).toHaveBeenCalledWith({ productCode: 'SAVINGS_STANDARD' });
    expect(snackOpen).toHaveBeenCalledWith('Account opened', undefined, { duration: 3000 });
    expect(navigate).toHaveBeenCalledWith(['/app/accounts', 'new-account-id']);
  });

  it.each([
    ['  Holiday  ', { productCode: 'SAVINGS_STANDARD', name: 'Holiday' }],
    ['   ', { productCode: 'SAVINGS_STANDARD' }],
  ])('name "%s" is sent as %o', async (typed, expected) => {
    await render();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    choose(SAVINGS.code);
    typeName(typed);
    await press();
    expect(open).toHaveBeenCalledWith(expected);
  });

  it('after success the button stays disabled until the page is left', async () => {
    await render();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    choose(SAVINGS.code);
    await press();
    await press();
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('without a product it asks to choose one and sends nothing', async () => {
    await render();
    await press();

    expect(open).not.toHaveBeenCalled();
    expect(text()).toContain('Choose an account type.');
  });

  it('a name of 51 characters is not sent', async () => {
    await render();
    choose(SAVINGS.code);
    typeName('a'.repeat(51));
    await press();

    expect(open).not.toHaveBeenCalled();
    expect(text()).toContain('At most 50 characters.');
  });

  it.each([
    [
      'TC-16',
      'ACCOUNT_LIMIT_REACHED',
      PERSONAL.code,
      'You already have the maximum number of accounts of this type.',
    ],
    [
      'TC-17',
      'PREREQUISITE_ACCOUNT_REQUIRED',
      SAVINGS.code,
      'You need an active personal account to open this account.',
    ],
  ])('%s shows %s and lets the user try again', async (_tc, code, productCode, message) => {
    await render();
    open.mockReturnValue(throwError(() => problem(code, 422)));
    choose(productCode);
    await press();

    expect(el.querySelector('[role=alert]')?.textContent).toContain(message);
    expect(submit().disabled).toBe(false);
    expect(snackOpen).not.toHaveBeenCalled();

    // Another choice removes the old message.
    choose(productCode === PERSONAL.code ? SAVINGS.code : PERSONAL.code);
    expect(text()).not.toContain(message);
  });

  it('a name error from the server is shown under the field', async () => {
    await render();
    open.mockReturnValue(
      throwError(() =>
        problem('VALIDATION_ERROR', 400, [{ field: 'name', code: 'TOO_LONG', message: '' }]),
      ),
    );
    choose(SAVINGS.code);
    typeName('Holiday');
    await press();

    expect(el.querySelector('mat-error')?.textContent).toContain('Too long.');
  });

  it('while opening, the button is disabled and a second press sends nothing', async () => {
    await render();
    const pending = new Subject<AccountDto>();
    open.mockReturnValue(pending);
    choose(SAVINGS.code);
    await press();
    await press();

    expect(open).toHaveBeenCalledTimes(1);
    expect(submit().disabled).toBe(true);
    expect(submit().textContent).toContain('Opening…');
  });

  it('if the products fail to load, "Try again" loads them again', async () => {
    products.mockReturnValueOnce(throwError(() => problem('INTERNAL_ERROR', 500)));
    await render();
    expect(text()).toContain('Something went wrong. Please try again.');
    expect(el.querySelector('form')).toBeNull();

    el.querySelector<HTMLButtonElement>('.banner button')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(products).toHaveBeenCalledTimes(2);
    expect(el.querySelectorAll('.product')).toHaveLength(2);
  });

  it('says so when there are no products', async () => {
    products.mockReturnValue(of([]));
    await render();
    expect(text()).toContain('No products are available right now.');
  });
});

describe('productRules', () => {
  it('personal account', () => {
    expect(productRules(PERSONAL)).toEqual([
      'No interest',
      'Transfers to any account',
      'ATM deposits and withdrawals',
      'Up to 2 accounts per customer',
    ]);
  });

  it('savings account', () => {
    expect(productRules(SAVINGS)).toEqual([
      '4,00% interest per year',
      'Transfers only to your own accounts',
      'No ATM',
      'Up to 2 accounts per customer',
      'Needs an active personal account',
    ]);
  });
});
