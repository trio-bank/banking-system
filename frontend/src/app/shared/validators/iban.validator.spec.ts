import { FormControl } from '@angular/forms';
import { checkIban, ibanValidator, normalizeIban } from './iban.validator';

// Test data from docs/09-testing.md
describe('ibanValidator', () => {
  it.each(['PL45999000121000000000000001', 'PL18999000121000000000000002'])(
    'accepts a valid IBAN %s',
    (iban) => {
      expect(checkIban(iban)).toBeNull();
    },
  );

  it('accepts spaces and lowercase', () => {
    expect(checkIban('pl45 9990 0012 1000 0000 0000 0001')).toBeNull();
  });

  it('accepts 26 digits without the PL prefix', () => {
    expect(checkIban('45999000121000000000000001')).toBeNull();
  });

  it('rejects a wrong checksum', () => {
    expect(checkIban('PL45999000121000000000000002')).toBe('INVALID_CHECKSUM');
  });

  it.each(['PL4599900012', 'DE45999000121000000000000001', 'PL4599900012100000000000000X'])(
    'rejects a wrong format %s',
    (iban) => {
      expect(checkIban(iban)).toBe('INVALID_FORMAT');
    },
  );

  it('normalizes input', () => {
    expect(normalizeIban(' pl45 9990 0012 1000 0000 0000 0001 ')).toBe(
      'PL45999000121000000000000001',
    );
    expect(normalizeIban('45999000121000000000000001')).toBe('PL45999000121000000000000001');
  });

  describe('as a form validator', () => {
    it('returns null for an empty value', () => {
      expect(ibanValidator(new FormControl(''))).toBeNull();
      expect(ibanValidator(new FormControl(null))).toBeNull();
    });

    it('returns the error code under the "iban" key', () => {
      expect(ibanValidator(new FormControl('PL45999000121000000000000002'))).toEqual({
        iban: 'INVALID_CHECKSUM',
      });
    });
  });
});
