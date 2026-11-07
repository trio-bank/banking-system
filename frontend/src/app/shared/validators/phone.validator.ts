import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** `+48 501 234 567` → `+48501234567`, the format the API wants. */
export function normalizePhone(phone: string): string {
  return phone.replace(/\s/g, '');
}

/** Polish number: exactly 9 digits after +48. Other countries: 8–15 digits in total. */
export function isValidPhone(phone: string): boolean {
  const normalized = normalizePhone(phone);
  return normalized.startsWith('+48')
    ? /^\+48[1-9]\d{8}$/.test(normalized)
    : /^\+[1-9]\d{7,14}$/.test(normalized);
}

/** For forms. Empty field is OK here — use `Validators.required` for that. */
export const phoneValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value: unknown = control.value;
  if (typeof value !== 'string' || value.trim() === '') {
    return null;
  }
  return isValidPhone(value) ? null : { phone: true };
};
