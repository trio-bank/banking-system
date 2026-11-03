import { FormControl } from '@angular/forms';
import { checkPesel, peselBirthDate, peselValidator } from './pesel.validator';

const isoDate = (date: Date | null) =>
  date &&
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

// Test data from docs/03 and docs/09
describe('peselValidator', () => {
  it.each([
    ['90051412343', '1990-05-14'],
    ['85110356782', '1985-11-03'],
    ['01222843219', '2001-02-28'],
    ['10270111119', '2010-07-01'],
  ])('accepts %s and derives birth date %s', (pesel, birthDate) => {
    expect(checkPesel(pesel)).toBeNull();
    expect(isoDate(peselBirthDate(pesel))).toBe(birthDate);
  });

  it('TC-03 rejects a PESEL with an invalid check digit', () => {
    expect(checkPesel('90051412344')).toBe('INVALID_CHECKSUM');
  });

  it.each([
    ['too short', '9005141234'],
    ['letters', '9005141234A'],
    ['month 13', '90131412343'],
    ['30 February', '90023012345'],
  ])('rejects a wrong format (%s)', (_, pesel) => {
    expect(checkPesel(pesel)).toBe('INVALID_FORMAT');
    expect(peselBirthDate(pesel)).toBeNull();
  });

  it('decodes the 1800s month offset', () => {
    expect(isoDate(peselBirthDate('99851412340'))).toBe('1899-05-14');
  });

  describe('as a form validator', () => {
    it('returns null for an empty value', () => {
      expect(peselValidator(new FormControl(''))).toBeNull();
    });

    it('TC-03 returns the error code under the "pesel" key', () => {
      expect(peselValidator(new FormControl('90051412344'))).toEqual({
        pesel: 'INVALID_CHECKSUM',
      });
    });
  });
});
