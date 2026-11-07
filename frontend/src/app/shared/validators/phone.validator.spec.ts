import { FormControl } from '@angular/forms';
import { isValidPhone, normalizePhone, phoneValidator } from './phone.validator';

describe('phoneValidator', () => {
  it.each([
    '+48501234567',
    '+48 501 234 567',
    ' +48 501234567 ',
    '+380 50 123 4567',
    '+12025550123',
  ])('accepts %s', (phone) => {
    expect(isValidPhone(phone)).toBe(true);
  });

  it.each([
    ['only the Polish code', '+48 '],
    ['8 digits after +48', '+48 501 234 56'],
    ['10 digits after +48', '+48 501 234 5678'],
    ['leading zero after +48', '+48 012 345 678'],
    ['no plus', '48501234567'],
    ['no country code', '501234567'],
    ['country code starting with 0', '+0501234567'],
    ['letters', '+48 501 abc 567'],
    ['too short foreign number', '+380 501'],
    ['too long foreign number', '+380 501 234 567 890 12'],
  ])('rejects %s', (_case, phone) => {
    expect(isValidPhone(phone)).toBe(false);
  });

  it('removes the spaces for the API', () => {
    expect(normalizePhone(' +48 501 234 567 ')).toBe('+48501234567');
  });

  it('leaves an empty field to Validators.required', () => {
    expect(phoneValidator(new FormControl(''))).toBeNull();
  });

  it('gives a phone error in a form', () => {
    expect(phoneValidator(new FormControl('+48 501'))).toEqual({ phone: true });
  });
});
