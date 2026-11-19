import { FormControl } from '@angular/forms';
import { accountNameValidator, formatRate, optionalAccountNameValidator } from './account-rules';

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
