import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, of, switchMap } from 'rxjs';
import { newIdempotencyKey } from '../../../core/api/idempotency-key';
import {
  AccountDto,
  Direction,
  Money,
  ProblemDetail,
  TransactionDto,
} from '../../../core/api/models';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { PaymentsApi } from '../../../core/api/services/payments-api';
import { errorMessage } from '../../../core/errors/error-messages';
import { formatMoney, IbanPipe, MoneyPipe, SignedAmountPipe } from '../../../shared/pipes';
import { MoneyError, moneyValidator, normalizeAmount } from '../../../shared/validators';

export type AtmKind = 'deposit' | 'withdrawal';

/** BR-19: banknotes only, so whole tens of PLN. */
export const ATM_LIMITS: Record<AtmKind, { min: number; max: number; step: number }> = {
  deposit: { min: 10, max: 20_000, step: 10 },
  withdrawal: { min: 10, max: 5_000, step: 10 },
};

export const QUICK_AMOUNTS = ['50', '100', '200', '500'];

const TEXTS: Record<
  AtmKind,
  {
    operation: string;
    noun: string;
    you: string;
    accountLabel: string;
    busy: string;
    done: string;
    again: string;
    direction: Direction;
  }
> = {
  deposit: {
    operation: 'ATM cash deposit',
    noun: 'deposit',
    you: 'You deposit',
    accountLabel: 'To account',
    busy: 'Depositing…',
    done: 'Cash deposited',
    again: 'New deposit',
    direction: 'INCOMING',
  },
  withdrawal: {
    operation: 'ATM cash withdrawal',
    noun: 'withdrawal',
    you: 'You withdraw',
    accountLabel: 'From account',
    busy: 'Withdrawing…',
    done: 'Cash withdrawn',
    again: 'New withdrawal',
    direction: 'OUTGOING',
  },
};

/** Grosze, so the balance check has no rounding errors (docs/07 §6). */
const grosze = (amount: Money) => Math.round(Number(amount) * 100);

/** 1022051 grosze → "10220.51". */
const fromGrosze = (value: number): Money =>
  `${Math.floor(value / 100)}.${String(value % 100).padStart(2, '0')}`;

type Step = 'form' | 'confirm' | 'result';

/** Text under the amount field for a validator error. */
export function amountErrorText(error: MoneyError, kind: AtmKind): string {
  const { min, max } = ATM_LIMITS[kind];
  switch (error) {
    case 'INVALID_FORMAT':
      return 'Enter an amount, e.g. 200.';
    case 'TOO_MANY_DECIMALS':
    case 'INVALID_STEP':
      return 'Only whole tens: the ATM works with banknotes.';
    default:
      return `From ${formatMoney(String(min))} to ${formatMoney(String(max))}.`;
  }
}

/**
 * One ATM operation: form → confirmation → result (docs/07 §4, §10).
 * The Idempotency-Key is made when the confirmation opens and stays the same on retry,
 * so a lost answer can't book the money twice. Back to the form = a new key next time.
 */
@Component({
  selector: 'app-atm-operation',
  imports: [
    IbanPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MoneyPipe,
    ReactiveFormsModule,
    RouterLink,
    SignedAmountPipe,
  ],
  templateUrl: './atm-operation.html',
  styleUrl: './atm-operation.scss',
})
export class AtmOperation {
  private readonly payments = inject(PaymentsApi);
  private readonly accountsApi = inject(AccountsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  readonly kind = input.required<AtmKind>();
  /** Personal accounts only (BR-20). Blocked ones are shown, but can't be chosen. */
  readonly accounts = input.required<AccountDto[]>();
  /** The account with its new balance after a successful operation. */
  readonly accountChanged = output<AccountDto>();
  /** The account to start with, e.g. the one chosen before switching Deposit / Withdraw. */
  readonly initialAccountId = input<string | null>(null);
  /** The chosen account, so the page can keep it for the other operation. */
  readonly accountPicked = output<string>();

  protected readonly quickAmounts = QUICK_AMOUNTS;
  protected readonly texts = computed(() => TEXTS[this.kind()]);
  protected readonly limitsHint = computed(() => {
    const { min, max } = ATM_LIMITS[this.kind()];
    return `From ${formatMoney(String(min))} to ${formatMoney(String(max))}, whole tens`;
  });

  protected readonly step = signal<Step>('form');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly transaction = signal<TransactionDto | null>(null);
  /** Fresh from C3 after the operation; null if that call failed. */
  protected readonly updatedAccount = signal<AccountDto | null>(null);
  private idempotencyKey: string | null = null;

  protected readonly form = new FormGroup({
    accountId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    amount: new FormControl('', {
      nonNullable: true,
      // A function, because kind() is not known yet when the field is made.
      validators: [Validators.required, (c: AbstractControl) => this.checkLimits(c)],
    }),
  });

  private readonly value = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly selected = computed(
    () => this.accounts().find((a) => a.id === this.value().accountId) ?? null,
  );
  protected readonly amount = computed(() => normalizeAmount(this.value().amount ?? '') ?? '');

  /**
   * Live preview under the amount: the balance if this operation goes through.
   * Null while the amount is empty or breaks a rule, the error under the field says why.
   */
  protected readonly balanceAfter = computed((): Money | null => {
    const account = this.selected();
    const amount = this.amount();
    // value() changes on every key; validity is already updated by then.
    if (!account || !amount || this.form.controls.amount.invalid) {
      return null;
    }
    const sign = this.kind() === 'deposit' ? 1 : -1;
    return fromGrosze(grosze(account.balance) + sign * grosze(amount));
  });

  /** Step heading, or the amount field on the form. */
  private readonly focusTarget = viewChild<ElementRef<HTMLElement>>('focusTarget');

  constructor() {
    // "Not above the balance" depends on the account, so check the amount again.
    this.form.controls.accountId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => {
        this.form.controls.amount.updateValueAndValidity();
        this.accountPicked.emit(id);
      });
    // Start with the given account or the first active one, and keep the choice
    // when the list is refreshed.
    effect(() => {
      const accounts = this.accounts();
      untracked(() => {
        const control = this.form.controls.accountId;
        const active = (id: string | null) =>
          accounts.some((a) => a.id === id && a.status === 'ACTIVE');
        if (!active(control.value)) {
          const initial = this.initialAccountId();
          control.setValue(
            active(initial) ? initial! : (accounts.find((a) => a.status === 'ACTIVE')?.id ?? ''),
          );
        }
      });
    });
  }

  protected pick(amount: string): void {
    this.form.controls.amount.setValue(amount);
    this.form.controls.amount.markAsTouched();
  }

  protected next(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    this.idempotencyKey = newIdempotencyKey();
    this.error.set(null);
    this.go('confirm');
  }

  protected back(): void {
    this.idempotencyKey = null; // the user may change the data, so it's a new operation
    this.error.set(null);
    this.go('form');
  }

  protected confirm(): void {
    const account = this.selected();
    if (this.sending() || !account || !this.idempotencyKey) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const request = { accountId: account.id, amount: this.amount() };
    const send =
      this.kind() === 'deposit'
        ? this.payments.atmDeposit(request, this.idempotencyKey)
        : this.payments.atmWithdrawal(request, this.idempotencyKey);
    send
      .pipe(
        // TransactionDto has no balance, so read the account again. If only that fails,
        // the money is still booked: show the result without the balance.
        switchMap((transaction) =>
          this.accountsApi.get(account.id).pipe(
            map((updated): [TransactionDto, AccountDto | null] => [transaction, updated]),
            catchError(() => of<[TransactionDto, null]>([transaction, null])),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ([transaction, updated]) => {
          this.sending.set(false);
          this.transaction.set(transaction);
          this.updatedAccount.set(updated);
          if (updated) {
            this.accountChanged.emit(updated);
          }
          this.go('result');
        },
        // Stay on the confirmation: "Confirm" again sends the same key.
        error: (problem: ProblemDetail) => {
          this.sending.set(false);
          this.error.set(errorMessage(problem.code));
        },
      });
  }

  protected startOver(): void {
    this.transaction.set(null);
    this.updatedAccount.set(null);
    this.form.controls.amount.reset('');
    this.go('form');
  }

  protected amountError(): string {
    const errors = this.form.controls.amount.errors ?? {};
    if (errors['required']) return 'Enter an amount.';
    if (errors['money']) return amountErrorText(errors['money'] as MoneyError, this.kind());
    if (errors['balance']) return `You have only ${formatMoney(errors['balance'] as Money)}.`;
    return '';
  }

  // Explicit types: the validator reads this.form, whose type depends on the validator.
  private checkLimits(control: AbstractControl): ValidationErrors | null {
    // Runs once while the field is made, before kind() is set: empty needs no limits.
    if (!control.value) {
      return null;
    }
    const limits = moneyValidator(ATM_LIMITS[this.kind()])(control);
    if (limits || this.kind() !== 'withdrawal') {
      return limits;
    }
    // BR-11. The backend checks it too (INSUFFICIENT_FUNDS), this only saves a round trip.
    const accountId: string = this.form.controls.accountId.value;
    const account = this.accounts().find((a) => a.id === accountId);
    const amount = normalizeAmount(control.value as string);
    return account && amount && grosze(amount) > grosze(account.balance)
      ? { balance: account.balance }
      : null;
  }

  /** New step: the clicked button is gone, so move the focus there (docs/07 §11). */
  private go(step: Step): void {
    this.step.set(step);
    afterNextRender(() => this.focusTarget()?.nativeElement.focus(), { injector: this.injector });
  }
}
