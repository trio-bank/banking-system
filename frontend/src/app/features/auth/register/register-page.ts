import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatStepper, MatStepperModule, StepperOrientation } from '@angular/material/stepper';
import { finalize, map } from 'rxjs';
import { ProblemDetail, RegisterRequest } from '../../../core/api/models';
import { AuthApi } from '../../../core/api/services/auth-api';
import { errorMessage, fieldErrorMessage } from '../../../core/errors/error-messages';
import {
  adultPeselValidator,
  checkPesel,
  normalizePhone,
  passwordValidator,
  peselBirthDate,
  peselValidator,
  phoneValidator,
} from '../../../shared/validators';
import { AuthLayout } from '../auth-layout/auth-layout';
import { PasswordStrength } from './password-strength';

const NAME_PATTERN = /^[\p{L} '-]*\p{L}[\p{L} '-]*$/u;
// Digits are fine next to letters: there are streets like "3 Maja".
const HAS_LETTER = /\p{L}/u;
// 12, 12A, 7/9, 7A/9B
const BUILDING_NUMBER = /^\s*\d+\p{L}?(\/\d+\p{L}?)?\s*$/u;

// These errors come without errors[], but they belong to one field.
const CONFLICT_FIELDS: Record<string, string> = {
  EMAIL_ALREADY_REGISTERED: 'email',
  PESEL_ALREADY_REGISTERED: 'pesel',
};

@Component({
  selector: 'app-register-page',
  imports: [
    AuthLayout,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatStepperModule,
    PasswordStrength,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './register-page.html',
  styleUrls: ['../auth-page.scss', './register-page.scss'],
})
export class RegisterPage {
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly stepper = viewChild.required(MatStepper);

  readonly form = this.fb.group({
    account: this.fb.group({
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required, passwordValidator('email')]],
    }),
    personal: this.fb.group({
      firstName: [
        '',
        [Validators.required, Validators.maxLength(100), Validators.pattern(NAME_PATTERN)],
      ],
      lastName: [
        '',
        [Validators.required, Validators.maxLength(100), Validators.pattern(NAME_PATTERN)],
      ],
      pesel: ['', [Validators.required, peselValidator, adultPeselValidator]],
      // Starts with the Polish code, but another one can be typed instead. Spaces are allowed here.
      phoneNumber: ['+48 ', [Validators.required, phoneValidator]],
    }),
    address: this.fb.group({
      street: [
        '',
        [Validators.required, Validators.maxLength(100), Validators.pattern(HAS_LETTER)],
      ],
      buildingNumber: [
        '',
        [Validators.required, Validators.maxLength(10), Validators.pattern(BUILDING_NUMBER)],
      ],
      apartmentNumber: ['', [Validators.maxLength(10), Validators.pattern(BUILDING_NUMBER)]],
      postalCode: ['', [Validators.required, Validators.pattern(/^\d{2}-\d{3}$/)]],
      city: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(HAS_LETTER)]],
    }),
  });

  protected readonly values = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    {
      initialValue: this.form.getRawValue(),
    },
  );

  /** Shown as soon as the PESEL is correct. */
  protected readonly birthDate = computed(() => {
    const pesel = this.values().personal.pesel.trim();
    return checkPesel(pesel) === null ? peselBirthDate(pesel) : null;
  });

  // Four labels don't fit in one row on a phone.
  protected readonly orientation = toSignal<StepperOrientation, StepperOrientation>(
    inject(BreakpointObserver)
      .observe('(max-width: 600px)')
      .pipe(map(({ matches }) => (matches ? 'vertical' : 'horizontal'))),
    { initialValue: 'horizontal' },
  );

  protected readonly submitting = signal(false);
  protected readonly banner = signal<string | null>(null);

  constructor() {
    // "Password contains email" depends on both fields, so check again when the email changes.
    const { email, password } = this.form.controls.account.controls;
    email.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => password.updateValueAndValidity());
  }

  /** Text under a field. `patternMessage` explains the expected format of this field. */
  protected errorOf(control: AbstractControl, patternMessage = ''): string {
    const errors = control.errors;
    if (!errors) return '';
    if (errors['server']) return errors['server'] as string;
    if (errors['required']) return fieldErrorMessage('REQUIRED');
    if (errors['pesel']) return fieldErrorMessage(errors['pesel'] as string);
    if (errors['underage']) return errorMessage('CUSTOMER_UNDERAGE');
    if (errors['password']) return fieldErrorMessage('WEAK_PASSWORD');
    if (errors['maxlength']) return fieldErrorMessage('TOO_LONG');
    return patternMessage || fieldErrorMessage('INVALID_FORMAT');
  }

  /** Stepper doesn't show errors by itself when Next is blocked. */
  protected next(group: FormGroup): void {
    group.markAllAsTouched();
    this.stepper().next();
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.banner.set(null);
    this.submitting.set(true);
    this.authApi
      .register(this.toRequest())
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (registration) =>
          void this.router.navigate(['/register/success'], { state: { registration } }),
        error: (problem: ProblemDetail) => this.showProblem(problem),
      });
  }

  private toRequest(): RegisterRequest {
    const { account, personal, address } = this.form.getRawValue();
    return {
      email: account.email.trim(),
      password: account.password,
      firstName: personal.firstName.trim(),
      lastName: personal.lastName.trim(),
      pesel: personal.pesel.trim(),
      phoneNumber: normalizePhone(personal.phoneNumber),
      address: {
        street: address.street.trim(),
        buildingNumber: address.buildingNumber.trim(),
        apartmentNumber: address.apartmentNumber.trim() || null,
        postalCode: address.postalCode,
        city: address.city.trim(),
        countryCode: null, // the backend sets PL
      },
    };
  }

  private showProblem(problem: ProblemDetail): void {
    const conflictField = CONFLICT_FIELDS[problem.code];
    const fieldErrors = conflictField
      ? [{ field: conflictField, message: errorMessage(problem.code) }]
      : problem.errors.map(({ field, code }) => ({ field, message: fieldErrorMessage(code) }));

    // Stepper steps in order. The API sends `email` or `address.postalCode`, here both are inside a step.
    const { account, personal, address } = this.form.controls;
    const steps: AbstractControl[] = [account, personal, address];

    let firstStep = -1;
    for (const { field, message } of fieldErrors) {
      const name = field.replace(/^address\./, '');
      const step = steps.findIndex((group) => group.get(name));
      if (step >= 0) {
        const control = steps[step].get(name);
        control?.setErrors({ server: message });
        control?.markAsTouched();
        firstStep = firstStep < 0 ? step : Math.min(firstStep, step);
      }
    }
    if (firstStep >= 0) {
      this.stepper().selectedIndex = firstStep;
      return;
    }

    if (problem.status >= 500 || problem.status === 0) {
      const errorId = problem.traceId ? ` Error ID: ${problem.traceId}` : '';
      this.snackBar.open(errorMessage(problem.code) + errorId, 'Close');
    } else {
      this.banner.set(errorMessage(problem.code));
    }
  }
}
