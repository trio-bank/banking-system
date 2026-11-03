import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export type MoneyError =
  | 'INVALID_FORMAT'
  | 'TOO_MANY_DECIMALS'
  | 'NOT_POSITIVE'
  | 'BELOW_MIN'
  | 'ABOVE_MAX'
  | 'INVALID_STEP';

/** Limits in PLN. By default: more than 0 and up to 1 000 000. */
export interface MoneyLimits {
  min?: number;
  max?: number;
  /** E.g. 10 for the ATM — it only gives out banknotes. */
  step?: number;
}

export const MAX_OPERATION_AMOUNT = 1_000_000;

/** User input → format for the API: `"1 250,5"` → `"1250.50"`. `null` if invalid. */
export function normalizeAmount(input: string): string | null {
  const grosze = parseGrosze(input);
  if (grosze === null || grosze === 'TOO_MANY_DECIMALS') {
    return null;
  }
  const zloty = Math.floor(grosze / 100);
  return `${zloty}.${String(grosze % 100).padStart(2, '0')}`;
}

/** Checks an amount. Works in grosze (whole numbers) so there are no rounding errors. */
export function checkAmount(input: string, limits: MoneyLimits = {}): MoneyError | null {
  const grosze = parseGrosze(input);
  if (grosze === null) return 'INVALID_FORMAT';
  if (grosze === 'TOO_MANY_DECIMALS') return 'TOO_MANY_DECIMALS';
  if (grosze <= 0) return 'NOT_POSITIVE';

  const { min, max = MAX_OPERATION_AMOUNT, step } = limits;
  if (min !== undefined && grosze < toGrosze(min)) return 'BELOW_MIN';
  if (grosze > toGrosze(max)) return 'ABOVE_MAX';
  if (step !== undefined && grosze % toGrosze(step) !== 0) return 'INVALID_STEP';
  return null;
}

/** For forms. ATM example: `moneyValidator({ min: 10, max: 5000, step: 10 })`. */
export function moneyValidator(limits: MoneyLimits = {}): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    const error = checkAmount(value, limits);
    return error ? { money: error } : null;
  };
}

/** `"1 250,5"` → 125050 grosze. */
function parseGrosze(input: string): number | 'TOO_MANY_DECIMALS' | null {
  const compact = input.replace(/\s/g, '').replace(',', '.');
  const match = /^(\d+)(?:\.(\d+))?$/.exec(compact);
  if (!match) {
    return null;
  }
  const [, whole, fraction = ''] = match;
  if (fraction.length > 2) {
    return 'TOO_MANY_DECIMALS';
  }
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}

function toGrosze(pln: number): number {
  return Math.round(pln * 100);
}
