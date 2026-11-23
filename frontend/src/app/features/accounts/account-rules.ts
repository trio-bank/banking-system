import { AbstractControl, ValidationErrors } from '@angular/forms';
import { AccountDto, AccountProductDto } from '../../core/api/models';
import { ENUM_LABELS } from '../../shared/pipes';

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

/** Accounts of this product that count towards its limit: blocked ones do, closed ones don't. */
export function openedCount(product: AccountProductDto, accounts: readonly AccountDto[]): number {
  return accounts.filter((a) => a.productCode === product.code && a.status !== 'CLOSED').length;
}

/**
 * Why the product can't be opened now (BR-07 limit, BR-08 prerequisite), or null if it can.
 * Only a hint for the UI: the backend checks the same rules again.
 */
export function cannotOpenReason(
  product: AccountProductDto,
  accounts: readonly AccountDto[],
): string | null {
  const opened = openedCount(product, accounts);
  if (opened >= product.maxAccountsPerCustomer) {
    return `Limit reached (${opened}/${product.maxAccountsPerCustomer})`;
  }
  const needed = product.prerequisiteType;
  if (needed && !accounts.some((a) => a.type === needed && a.status === 'ACTIVE')) {
    const type = ENUM_LABELS[needed] ?? needed;
    return `Needs an active ${type.toLowerCase()} account`;
  }
  return null;
}
