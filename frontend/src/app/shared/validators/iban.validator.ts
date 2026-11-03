import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Same error codes as the backend. */
export type IbanError = 'INVALID_FORMAT' | 'INVALID_CHECKSUM';

/** Removes spaces, makes it uppercase, adds `PL` if the user typed only digits. */
export function normalizeIban(value: string): string {
  const compact = value.replace(/\s+/g, '').toUpperCase();
  return /^\d{26}$/.test(compact) ? `PL${compact}` : compact;
}

/** Checks a Polish IBAN: `PL` + 26 digits and a correct checksum. */
export function checkIban(value: string): IbanError | null {
  const iban = normalizeIban(value);
  if (!/^PL\d{26}$/.test(iban)) {
    return 'INVALID_FORMAT';
  }
  return mod97(iban) === 1 ? null : 'INVALID_CHECKSUM';
}

/** For forms. Empty field is OK here — use `Validators.required` for that. */
export const ibanValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value: unknown = control.value;
  if (typeof value !== 'string' || value.trim() === '') {
    return null;
  }
  const error = checkIban(value);
  return error ? { iban: error } : null;
};

/** Standard IBAN check: valid when the result is 1. */
function mod97(iban: string): number {
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  let remainder = 0;
  for (const char of rearranged) {
    const digits = /\d/.test(char) ? char : String(char.charCodeAt(0) - 55);
    for (const digit of digits) {
      remainder = (remainder * 10 + Number(digit)) % 97;
    }
  }
  return remainder;
}
