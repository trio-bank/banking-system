import { AbstractControl, ValidationErrors } from '@angular/forms';

export const ACCOUNT_NAME_MAX = 50; // BR-28

/** `"4.0000"` → `4,00%`. Keeps up to 4 decimals if the rate has them. */
export function formatRate(rate: string): string {
  const format = new Intl.NumberFormat('pl-PL', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  });
  return `${format.format(Number(rate))}%`;
}

/** Rename (BR-28): 1–50 characters, counted after trimming, like the backend does. */
export function accountNameValidator(control: AbstractControl<string>): ValidationErrors | null {
  if (!control.value.trim()) {
    return { required: true };
  }
  return optionalAccountNameValidator(control);
}

/** Opening an account: the name may be empty (the backend uses the product name), else ≤ 50. */
export function optionalAccountNameValidator(
  control: AbstractControl<string>,
): ValidationErrors | null {
  const length = control.value.trim().length;
  return length > ACCOUNT_NAME_MAX
    ? { maxlength: { requiredLength: ACCOUNT_NAME_MAX, actualLength: length } }
    : null;
}
