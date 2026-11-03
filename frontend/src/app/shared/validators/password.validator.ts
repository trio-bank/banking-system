import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Which password rules are not met — shown to the user while typing. */
export interface PasswordRuleFailures {
  minLength?: true;
  maxLength?: true;
  lowercase?: true;
  uppercase?: true;
  digit?: true;
  special?: true;
  containsEmail?: true;
}

export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;

/** Checks the password rules. Empty result = strong password. */
export function passwordRuleFailures(
  password: string,
  email?: string | null,
): PasswordRuleFailures {
  const failures: PasswordRuleFailures = {};
  if (password.length < PASSWORD_MIN_LENGTH) failures.minLength = true;
  if (password.length > PASSWORD_MAX_LENGTH) failures.maxLength = true;
  if (!/\p{Ll}/u.test(password)) failures.lowercase = true;
  if (!/\p{Lu}/u.test(password)) failures.uppercase = true;
  if (!/\d/.test(password)) failures.digit = true;
  if (!/[^\p{L}\d\s]/u.test(password)) failures.special = true;

  const localPart = email?.split('@')[0]?.trim().toLowerCase() ?? '';
  // Skip very short names like "a@x.pl", or almost no password would pass.
  if (localPart.length >= 3 && password.toLowerCase().includes(localPart)) {
    failures.containsEmail = true;
  }
  return failures;
}

/**
 * For forms. Give the email field name to also check "password contains email".
 * When the email changes, check the password again.
 */
export function passwordValidator(emailControlName?: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value === '') {
      return null;
    }
    const email: unknown = emailControlName ? control.parent?.get(emailControlName)?.value : null;
    const failures = passwordRuleFailures(value, typeof email === 'string' ? email : null);
    return Object.keys(failures).length > 0 ? { password: failures } : null;
  };
}
