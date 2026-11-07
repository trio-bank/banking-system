import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatStepper } from '@angular/material/stepper';
import { By } from '@angular/platform-browser';
import { of, Subject, throwError } from 'rxjs';
import { FieldError, ProblemDetail, RegistrationResponse } from '../../../core/api/models';
import { NEW_PERSONAL_ACCOUNT } from '../../../core/api/mocks/mock-data';
import { AuthApi } from '../../../core/api/services/auth-api';
import { RegisterPage } from './register-page';

const VALID = {
  account: { email: 'new.user@example.com', password: 'Str0ng!Passw0rd' },
  personal: {
    firstName: 'Jan',
    lastName: 'Kowalski',
    pesel: '85110356782',
    phoneNumber: '+48501234567',
  },
  address: {
    street: 'Krakowska',
    buildingNumber: '12A',
    apartmentNumber: '',
    postalCode: '45-018',
    city: 'Opole',
  },
};

const RESPONSE: RegistrationResponse = {
  userId: 'user-id',
  customerNumber: '10000002',
  email: VALID.account.email,
  account: NEW_PERSONAL_ACCOUNT,
};

const problemOf = (
  status: number,
  code: string,
  errors: FieldError[] = [],
  traceId = 'trace-1',
): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status,
  detail: '',
  instance: '/auth/register',
  code,
  traceId,
  timestamp: '2026-11-07T12:00:00Z',
  errors,
});

describe('RegisterPage', () => {
  let fixture: ComponentFixture<RegisterPage>;
  let page: RegisterPage;
  const register = vi.fn();

  beforeEach(async () => {
    register.mockReset();
    TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideRouter([]), { provide: AuthApi, useValue: { register } }],
    });
    fixture = TestBed.createComponent(RegisterPage);
    page = fixture.componentInstance;
    await fixture.whenStable();
  });

  const stepper = () =>
    fixture.debugElement.query(By.directive(MatStepper)).componentInstance as MatStepper;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const render = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
  };

  /** The Next/Create button of the step that is open now. */
  const pressMainButton = async (step: number) => {
    const forms = (fixture.nativeElement as HTMLElement).querySelectorAll('form');
    forms[step].dispatchEvent(new Event('submit', { cancelable: true }));
    await render();
  };

  /** Presses Next on the first three steps, like a user does. */
  const goToSummary = async () => {
    await pressMainButton(0);
    await pressMainButton(1);
    await pressMainButton(2);
  };

  const submitValidForm = async () => {
    page.form.setValue(VALID);
    await goToSummary();
    await pressMainButton(3);
  };

  describe('steps', () => {
    it('stays on the step while its fields are invalid and shows the errors', async () => {
      await pressMainButton(0);
      expect(stepper().selectedIndex).toBe(0);
      expect(text()).toContain('This field is required.');
    });

    it('goes to the next step when the fields are valid', async () => {
      page.form.controls.account.setValue(VALID.account);
      await pressMainButton(0);
      expect(stepper().selectedIndex).toBe(1);
    });
  });

  describe('account step', () => {
    it('TC-05 shows which password rule is not met', async () => {
      page.form.controls.account.controls.password.setValue('NoSpecialChar123');
      await render();
      const failed = [...(fixture.nativeElement as HTMLElement).querySelectorAll('li:not(.met)')];
      expect(failed.map((rule) => rule.textContent)).toEqual([
        expect.stringContaining('A special character'),
      ]);
      expect(page.form.controls.account.controls.password.hasError('password')).toBe(true);
    });

    it('checks the password again when the email changes', () => {
      const { email, password } = page.form.controls.account.controls;
      password.setValue('Jan.Kowalski!123');
      expect(password.valid).toBe(true);

      email.setValue('jan.kowalski@example.com');
      expect(password.errors?.['password']).toEqual({ containsEmail: true });
    });
  });

  describe('personal step', () => {
    const pesel = () => page.form.controls.personal.controls.pesel;

    it('TC-03 rejects a PESEL with a wrong check digit', async () => {
      pesel().setValue('90051412344');
      pesel().markAsTouched();
      await render();
      expect(pesel().errors).toEqual({ pesel: 'INVALID_CHECKSUM' });
      expect(text()).not.toContain('Date of birth');
    });

    it('shows the date of birth from a correct PESEL', async () => {
      pesel().setValue('90051412343');
      await render();
      expect(text()).toContain('Date of birth: 14.05.1990');
    });

    it('starts the phone number with the Polish code, which is not enough to go further', () => {
      const control = page.form.controls.personal.controls.phoneNumber;
      expect(control.value).toBe('+48 ');
      expect(control.valid).toBe(false);
    });

    it.each(['+48 501 234 567', '+48501234567', '+380 50 123 4567'])(
      'accepts phone %s',
      (phone) => {
        const control = page.form.controls.personal.controls.phoneNumber;
        control.setValue(phone);
        expect(control.valid).toBe(true);
      },
    );

    it.each(['501234567', '+0501234567', '+48 501 abc', '+48 501 234', '+48 501 234 5678'])(
      'rejects phone %s',
      (phone) => {
        const control = page.form.controls.personal.controls.phoneNumber;
        control.setValue(phone);
        expect(control.hasError('phone')).toBe(true);
      },
    );

    it('TC-04 stops a person under 18 on this step, before anything is sent', async () => {
      page.form.controls.account.setValue(VALID.account);
      await pressMainButton(0);
      page.form.controls.personal.setValue({ ...VALID.personal, pesel: '10270111119' });
      await pressMainButton(1);

      expect(stepper().selectedIndex).toBe(1);
      expect(register).not.toHaveBeenCalled();
      expect(text()).toContain('You must be at least 18 years old to open an account.');
    });

    it('accepts names with spaces, hyphens and apostrophes, but not digits', () => {
      const control = page.form.controls.personal.controls.lastName;
      control.setValue("Nowak-O'Brien Żółć");
      expect(control.valid).toBe(true);
      control.setValue('Nowak1');
      expect(control.hasError('pattern')).toBe(true);
    });
  });

  describe('address step', () => {
    const controls = () => page.form.controls.address.controls;

    it.each(['Krakowska', '3 Maja', '11 Listopada', 'Świętokrzyska'])(
      'accepts street %s',
      (name) => {
        controls().street.setValue(name);
        expect(controls().street.valid).toBe(true);
      },
    );

    it.each(['Opole', 'Zielona Góra'])('accepts city %s', (name) => {
      controls().city.setValue(name);
      expect(controls().city.valid).toBe(true);
    });

    it.each(['12345', '   ', '12-34'])('rejects street and city without letters: "%s"', (name) => {
      controls().street.setValue(name);
      controls().city.setValue(name);
      expect(controls().street.hasError('pattern')).toBe(true);
      expect(controls().city.hasError('pattern')).toBe(true);
    });

    it.each(['12', '12A', '7/9', '7A/9B', ' 12 '])(
      'accepts building and apartment %s',
      (number) => {
        controls().buildingNumber.setValue(number);
        controls().apartmentNumber.setValue(number);
        expect(controls().buildingNumber.valid).toBe(true);
        expect(controls().apartmentNumber.valid).toBe(true);
      },
    );

    it.each(['abc', 'A12', '12AB', '7/', '/9'])('rejects building and apartment %s', (number) => {
      controls().buildingNumber.setValue(number);
      controls().apartmentNumber.setValue(number);
      expect(controls().buildingNumber.hasError('pattern')).toBe(true);
      expect(controls().apartmentNumber.hasError('pattern')).toBe(true);
    });

    it('allows an empty apartment, but not an empty building', () => {
      controls().apartmentNumber.setValue('');
      controls().buildingNumber.setValue('');
      expect(controls().apartmentNumber.valid).toBe(true);
      expect(controls().buildingNumber.hasError('required')).toBe(true);
    });
  });

  describe('submit', () => {
    it('TC-01 sends the data and opens the success page with the result', async () => {
      register.mockReturnValue(of(RESPONSE));
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      page.form.setValue({
        ...VALID,
        personal: { ...VALID.personal, firstName: '  Jan ', phoneNumber: '+48 501 234 567' },
      });
      await goToSummary();
      await pressMainButton(3);

      expect(register).toHaveBeenCalledWith({
        ...VALID.account,
        ...VALID.personal,
        address: { ...VALID.address, apartmentNumber: null, countryCode: null },
      });
      expect(navigate).toHaveBeenCalledWith(['/register/success'], {
        state: { registration: RESPONSE },
      });
    });

    it('sends only one request on a double click', async () => {
      register.mockReturnValue(new Subject());
      await submitValidForm();
      await pressMainButton(3);
      expect(register).toHaveBeenCalledTimes(1);
    });
  });

  describe('backend errors', () => {
    it('TC-02 shows a taken email under the field on the first step', async () => {
      register.mockReturnValue(throwError(() => problemOf(409, 'EMAIL_ALREADY_REGISTERED')));
      await submitValidForm();

      expect(stepper().selectedIndex).toBe(0);
      expect(page.form.controls.account.controls.email.errors).toEqual({
        server: 'This email is already registered.',
      });
      expect(text()).toContain('This email is already registered.');
    });

    it('shows a taken PESEL under the field on the second step', async () => {
      register.mockReturnValue(throwError(() => problemOf(409, 'PESEL_ALREADY_REGISTERED')));
      await submitValidForm();

      expect(stepper().selectedIndex).toBe(1);
      expect(text()).toContain('A customer with this PESEL already exists.');
    });

    it('clears the backend error when the user changes the field', async () => {
      register.mockReturnValue(throwError(() => problemOf(409, 'EMAIL_ALREADY_REGISTERED')));
      await submitValidForm();

      const email = page.form.controls.account.controls.email;
      email.setValue('other@example.com');
      expect(email.valid).toBe(true);
    });

    it('maps 400 errors[] to nested fields and opens the earliest step', async () => {
      const errors = [
        { field: 'address.postalCode', code: 'INVALID_FORMAT', message: '' },
        { field: 'pesel', code: 'INVALID_CHECKSUM', message: '' },
      ];
      register.mockReturnValue(throwError(() => problemOf(400, 'VALIDATION_ERROR', errors)));
      await submitValidForm();

      expect(stepper().selectedIndex).toBe(1);
      expect(page.form.controls.address.controls.postalCode.errors).toEqual({
        server: 'Wrong format.',
      });
      expect(page.form.controls.personal.controls.pesel.errors).toEqual({
        server: 'The number is not valid.',
      });
    });

    it('shows a business error from the backend in the banner', async () => {
      register.mockReturnValue(throwError(() => problemOf(422, 'CUSTOMER_UNDERAGE')));
      await submitValidForm();

      expect(stepper().selectedIndex).toBe(3);
      const banner = (fixture.nativeElement as HTMLElement).querySelector('.banner');
      expect(banner?.textContent).toContain('You must be at least 18 years old');
    });

    it('shows a server error in a snackbar with the error id', async () => {
      const open = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      register.mockReturnValue(throwError(() => problemOf(500, 'INTERNAL_ERROR')));
      await submitValidForm();

      expect(open).toHaveBeenCalledWith(
        'Something went wrong. Please try again. Error ID: trace-1',
        'Close',
      );
    });

    it('allows sending again after an error', async () => {
      register.mockReturnValueOnce(throwError(() => problemOf(500, 'INTERNAL_ERROR')));
      await submitValidForm();
      register.mockReturnValueOnce(of(RESPONSE));
      vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      await pressMainButton(3);
      expect(register).toHaveBeenCalledTimes(2);
    });
  });
});
