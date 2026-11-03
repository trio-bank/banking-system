import { FormControl } from '@angular/forms';
import { checkAmount, moneyValidator, normalizeAmount } from './money.validator';

// Rules BR-10 and BR-19 from docs/02-requirements.md
describe('moneyValidator', () => {
  it.each(['150', '150.5', '150.50', '150,50', '1 250,00', '0.01', '1000000.00'])(
    'accepts %s',
    (amount) => {
      expect(checkAmount(amount)).toBeNull();
    },
  );

  it('TC-29 rejects more than 2 decimals', () => {
    expect(checkAmount('10.001')).toBe('TOO_MANY_DECIMALS');
  });

  it('TC-29 rejects zero', () => {
    expect(checkAmount('0')).toBe('NOT_POSITIVE');
    expect(checkAmount('0.00')).toBe('NOT_POSITIVE');
  });

  it('rejects more than 1 000 000.00 per operation', () => {
    expect(checkAmount('1000000.01')).toBe('ABOVE_MAX');
  });

  it.each(['abc', '-5', '1.2.3', '12,', '.5'])('rejects a wrong format %s', (amount) => {
    expect(checkAmount(amount)).toBe('INVALID_FORMAT');
  });

  describe('ATM withdrawal limits (10–5 000, multiples of 10)', () => {
    const atm = { min: 10, max: 5000, step: 10 };

    it('TC-35 rejects an amount that is not a multiple of 10', () => {
      expect(checkAmount('15', atm)).toBe('INVALID_STEP');
    });

    it('accepts multiples of 10 within limits', () => {
      expect(checkAmount('20', atm)).toBeNull();
      expect(checkAmount('5000.00', atm)).toBeNull();
    });

    it('rejects amounts outside the limits', () => {
      expect(checkAmount('5', atm)).toBe('BELOW_MIN');
      expect(checkAmount('5010', atm)).toBe('ABOVE_MAX');
    });
  });

  describe('normalizeAmount', () => {
    it.each([
      ['1 250,5', '1250.50'],
      ['150', '150.00'],
      ['0,01', '0.01'],
      ['007.5', '7.50'],
    ])('%s → %s', (input, expected) => {
      expect(normalizeAmount(input)).toBe(expected);
    });

    it('returns null for invalid input', () => {
      expect(normalizeAmount('abc')).toBeNull();
      expect(normalizeAmount('10.001')).toBeNull();
    });
  });

  describe('as a form validator', () => {
    it('returns null for an empty value', () => {
      expect(moneyValidator()(new FormControl(''))).toBeNull();
    });

    it('returns the error code under the "money" key', () => {
      expect(moneyValidator()(new FormControl('10.001'))).toEqual({ money: 'TOO_MANY_DECIMALS' });
    });
  });
});
