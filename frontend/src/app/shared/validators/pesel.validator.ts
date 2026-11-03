import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Same error codes as the backend. */
export type PeselError = 'INVALID_FORMAT' | 'INVALID_CHECKSUM';

const WEIGHTS = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3];

/** PESEL hides the century in the month: +20 = 2000s, +80 = 1800s, etc. */
const CENTURY_BY_OFFSET: Record<number, number> = {
  0: 1900,
  20: 2000,
  40: 2100,
  60: 2200,
  80: 1800,
};

/** Birth date from a PESEL, or `null` if it's not a real date. */
export function peselBirthDate(pesel: string): Date | null {
  if (!/^\d{11}$/.test(pesel)) {
    return null;
  }
  const yy = Number(pesel.slice(0, 2));
  const mm = Number(pesel.slice(2, 4));
  const dd = Number(pesel.slice(4, 6));

  const offset = Math.floor(mm / 20) * 20;
  const century = CENTURY_BY_OFFSET[offset];
  const month = mm - offset;
  if (century === undefined || month < 1 || month > 12) {
    return null;
  }

  const year = century + yy;
  const date = new Date(year, month - 1, dd);
  // Catches dates like 30 February (JS would quietly turn it into March).
  const isRealDate =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === dd;
  return isRealDate ? date : null;
}

/** Checks a PESEL: 11 digits, real date, correct last digit. */
export function checkPesel(pesel: string): PeselError | null {
  if (peselBirthDate(pesel) === null) {
    return 'INVALID_FORMAT';
  }
  const sum = WEIGHTS.reduce((acc, weight, i) => acc + weight * Number(pesel[i]), 0);
  const checkDigit = (10 - (sum % 10)) % 10;
  return checkDigit === Number(pesel[10]) ? null : 'INVALID_CHECKSUM';
}

/** For forms. Empty field is OK here — use `Validators.required` for that. */
export const peselValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value: unknown = control.value;
  if (typeof value !== 'string' || value.trim() === '') {
    return null;
  }
  const error = checkPesel(value.trim());
  return error ? { pesel: error } : null;
};
