import { FormControl } from '@angular/forms';
import { AccountDto, AccountStatus } from '../../core/api/models';
import { MOCK_PRODUCTS } from '../../../testing/mock-bank';
import {
  accountNameValidator,
  cannotOpenReason,
  formatRate,
  openedCount,
  optionalAccountNameValidator,
} from './account-rules';

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

describe('optionalAccountNameValidator', () => {
  const check = (value: string) =>
    optionalAccountNameValidator(new FormControl(value, { nonNullable: true }));

  it.each(['', '   ', 'Holiday savings', `  ${'a'.repeat(50)}  `])('"%s" is valid', (name) => {
    expect(check(name)).toBeNull();
  });

  it('51 characters → maxlength', () => {
    expect(check('a'.repeat(51))).toEqual({ maxlength: { requiredLength: 50, actualLength: 51 } });
  });
});

describe('cannotOpenReason', () => {
  const [PERSONAL, SAVINGS] = MOCK_PRODUCTS;
  const personal = (status: AccountStatus = 'ACTIVE') =>
    ({ productCode: PERSONAL.code, type: 'PERSONAL', status }) as AccountDto;
  const savings = (status: AccountStatus = 'ACTIVE') =>
    ({ productCode: SAVINGS.code, type: 'SAVINGS', status }) as AccountDto;

  it('allows a product below its limit', () => {
    expect(cannotOpenReason(PERSONAL, [personal()])).toBeNull();
    expect(cannotOpenReason(SAVINGS, [personal(), savings()])).toBeNull();
  });

  it('BR-07 counts blocked accounts but not closed ones', () => {
    expect(openedCount(PERSONAL, [personal(), personal('BLOCKED'), personal('CLOSED')])).toBe(2);
    expect(cannotOpenReason(PERSONAL, [personal(), personal('BLOCKED')])).toBe(
      'Limit reached (2/2)',
    );
    expect(cannotOpenReason(PERSONAL, [personal(), personal('CLOSED')])).toBeNull();
  });

  it('BR-08 savings needs an active personal account', () => {
    expect(cannotOpenReason(SAVINGS, [])).toBe('Needs an active personal account');
    expect(cannotOpenReason(SAVINGS, [personal('BLOCKED')])).toBe(
      'Needs an active personal account',
    );
  });

  it('the limit is shown before the missing account', () => {
    expect(cannotOpenReason(SAVINGS, [savings(), savings()])).toBe('Limit reached (2/2)');
  });
});
