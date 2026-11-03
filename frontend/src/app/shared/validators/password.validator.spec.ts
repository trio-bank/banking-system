import { FormControl, FormGroup } from '@angular/forms';
import { passwordRuleFailures, passwordValidator } from './password.validator';

// Rule BR-02 from docs/02-requirements.md
describe('passwordValidator', () => {
  it('accepts the strong test password', () => {
    expect(passwordRuleFailures('Str0ng!Passw0rd')).toEqual({});
  });

  it('TC-05 reports a missing special character', () => {
    expect(passwordRuleFailures('Str0ngPassw0rd')).toEqual({ special: true });
  });

  it('reports every failed rule at once', () => {
    expect(passwordRuleFailures('abc')).toEqual({
      minLength: true,
      uppercase: true,
      digit: true,
      special: true,
    });
  });

  it('rejects passwords longer than 128 characters', () => {
    expect(passwordRuleFailures(`Aa1!${'x'.repeat(125)}`)).toEqual({ maxLength: true });
  });

  it('rejects a password containing the email local part (case-insensitive)', () => {
    expect(passwordRuleFailures('Anna.Nowak!2026x', 'anna.nowak@example.com')).toEqual({
      containsEmail: true,
    });
  });

  it('ignores very short email local parts', () => {
    expect(passwordRuleFailures('Str0ng!Passw0rd', 'st@example.com')).toEqual({});
  });

  describe('as a form validator', () => {
    it('returns null for an empty value', () => {
      expect(passwordValidator()(new FormControl(''))).toBeNull();
    });

    it('TC-05 returns failed rules under the "password" key', () => {
      expect(passwordValidator()(new FormControl('Str0ngPassw0rd'))).toEqual({
        password: { special: true },
      });
    });

    it('reads the email from the sibling control', () => {
      const form = new FormGroup({
        email: new FormControl('anna.nowak@example.com'),
        password: new FormControl('Anna.Nowak!2026x', passwordValidator('email')),
      });
      // When the form is created, the password field doesn't see the email yet — check again.
      form.controls.password.updateValueAndValidity();
      expect(form.controls.password.errors).toEqual({ password: { containsEmail: true } });

      form.controls.email.setValue('jan@example.com');
      form.controls.password.updateValueAndValidity();
      expect(form.controls.password.errors).toBeNull();
    });
  });
});
