import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  AccountDto,
  AccountProductDto,
  OpenAccountRequest,
  ProblemDetail,
} from '../../../core/api/models';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { errorMessage, fieldErrorMessage } from '../../../core/errors/error-messages';
import { EnumLabelPipe, ENUM_LABELS } from '../../../shared/pipes';
import {
  ACCOUNT_NAME_MAX,
  cannotOpenReason,
  formatRate,
  optionalAccountNameValidator,
} from '../account-rules';

/** The "main rules" of a product (US-07), as short lines for its card. */
export function productRules(product: AccountProductDto): string[] {
  const rules = [
    Number(product.interestRate) > 0
      ? `${formatRate(product.interestRate)} interest per year`
      : 'No interest',
    product.outgoingTransferPolicy === 'OWN_ACCOUNTS_ONLY'
      ? 'Transfers only to your own accounts'
      : 'Transfers to any account',
    product.atmAllowed ? 'ATM deposits and withdrawals' : 'No ATM',
    `Up to ${product.maxAccountsPerCustomer} accounts per customer`,
  ];
  if (product.prerequisiteType) {
    const type = ENUM_LABELS[product.prerequisiteType] ?? product.prerequisiteType;
    rules.push(`Needs an active ${type.toLowerCase()} account`);
  }
  return rules;
}

/** A product card: the product and why it can't be opened now (null = it can). */
export interface ProductOption {
  product: AccountProductDto;
  blockedBy: string | null;
}

/**
 * Product cards + optional name → C2 (US-07). Products over the limit (BR-07) or without the
 * needed account (BR-08) are greyed out up front; the backend still checks, its 422 is a message.
 */
@Component({
  selector: 'app-open-account-page',
  imports: [
    EnumLabelPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './open-account-page.html',
  styleUrl: './open-account-page.scss',
})
export class OpenAccountPage {
  private readonly api = inject(AccountsApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly products = signal<AccountProductDto[] | null>(null);
  private readonly accounts = signal<AccountDto[]>([]);
  protected readonly options = computed<ProductOption[] | null>(
    () =>
      this.products()?.map((product) => ({
        product,
        blockedBy: cannotOpenReason(product, this.accounts()),
      })) ?? null,
  );
  /** Every product is at its limit or not allowed: there is nothing to open. */
  protected readonly nothingToOpen = computed(
    () => !!this.options()?.length && this.options()!.every((o) => o.blockedBy !== null),
  );
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly nameMax = ACCOUNT_NAME_MAX;
  protected readonly productRules = productRules;

  protected readonly form = new FormGroup({
    productCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
    name: new FormControl('', { nonNullable: true, validators: optionalAccountNameValidator }),
  });

  private readonly productCode = toSignal(this.form.controls.productCode.valueChanges, {
    initialValue: '',
  });
  /** Its name is the placeholder: that's what the backend uses when the field is empty. */
  protected readonly selected = computed(
    () => this.products()?.find((p) => p.code === this.productCode()) ?? null,
  );

  constructor() {
    this.loadProducts();
    // A new choice makes the old "limit reached" message wrong.
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.submitError.set(null));
  }

  protected loadProducts(): void {
    this.loading.set(true);
    this.loadError.set(null);
    // The accounts tell which products are already full.
    forkJoin([this.api.products(), this.api.list()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([products, accounts]) => {
          this.accounts.set(accounts);
          this.products.set(products);
          this.loading.set(false);
        },
        error: (problem: ProblemDetail) => {
          this.loadError.set(errorMessage(problem.code));
          this.loading.set(false);
        },
      });
  }

  protected open(): void {
    this.form.markAllAsTouched();
    this.form.controls.name.updateValueAndValidity(); // drops the last server error
    if (this.saving() || this.form.invalid) {
      return;
    }
    const { productCode, name } = this.form.getRawValue();
    // No name at all instead of "": the backend then sets the product name (ADR-0011).
    const request: OpenAccountRequest = name.trim()
      ? { productCode, name: name.trim() }
      : { productCode };

    this.saving.set(true);
    this.submitError.set(null);
    this.api
      .open(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (account) => {
          this.snackBar.open('Account opened', undefined, { duration: 3000 });
          // Details show the new IBAN (with Copy) and the zero balance.
          void this.router.navigate(['/app/accounts', account.id]);
        },
        error: (problem: ProblemDetail) => {
          this.saving.set(false);
          const fieldError = problem.errors.find((e) => e.field === 'name');
          if (fieldError) {
            this.form.controls.name.setErrors({ server: fieldErrorMessage(fieldError.code) });
          } else {
            this.submitError.set(errorMessage(problem.code));
          }
        },
      });
  }

  protected nameError(): string {
    const errors = this.form.controls.name.errors ?? {};
    if (errors['server']) return errors['server'] as string;
    if (errors['maxlength']) return `At most ${ACCOUNT_NAME_MAX} characters.`;
    return '';
  }
}
