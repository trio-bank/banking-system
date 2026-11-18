import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AccountDto, AccountHistoryItemDto, Money, ProblemDetail } from '../../core/api/models';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { AuthStore } from '../../core/auth/auth.store';
import { errorMessage } from '../../core/errors/error-messages';
import { EnumLabelPipe, IbanPipe, MoneyPipe, SignedAmountPipe } from '../../shared/pipes';

const LAST_OPERATIONS = 5;

/** Sum of the active accounts (US-06). Counted in grosze, so there are no rounding errors. */
export function totalBalance(accounts: readonly AccountDto[]): Money {
  const grosze = accounts
    .filter((account) => account.status === 'ACTIVE')
    .reduce((sum, account) => sum + Math.round(Number(account.balance) * 100), 0);
  return `${Math.floor(grosze / 100)}.${String(grosze % 100).padStart(2, '0')}`;
}

/** Customer home: greeting, total, account cards and the last operations of the first account. */
@Component({
  selector: 'app-dashboard-page',
  imports: [
    DatePipe,
    EnumLabelPipe,
    IbanPipe,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    RouterLink,
    SignedAmountPipe,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage {
  private readonly api = inject(AccountsApi);
  private readonly store = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly firstName = computed(() => this.store.currentUser()?.customer?.firstName);
  protected readonly previousLoginAt = this.store.previousLoginAt;

  protected readonly accounts = signal<AccountDto[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly total = computed(() => totalBalance(this.accounts() ?? []));
  /** The backend sorts personal accounts first, so this is the main one. */
  protected readonly firstAccount = computed(() => this.accounts()?.[0] ?? null);

  // The operations have their own states: if they fail, the accounts are still shown.
  protected readonly operations = signal<AccountHistoryItemDto[] | null>(null);
  protected readonly operationsLoading = signal(false);
  protected readonly operationsError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (accounts) => {
          this.accounts.set(accounts);
          this.loading.set(false);
          this.loadOperations();
        },
        error: (problem: ProblemDetail) => {
          this.error.set(errorMessage(problem.code));
          this.loading.set(false);
        },
      });
  }

  protected loadOperations(): void {
    const account = this.firstAccount();
    if (!account) {
      return;
    }
    this.operationsLoading.set(true);
    this.operationsError.set(null);
    this.api
      .history(account.id, { size: LAST_OPERATIONS })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.operations.set(page.content);
          this.operationsLoading.set(false);
        },
        error: (problem: ProblemDetail) => {
          this.operationsError.set(errorMessage(problem.code));
          this.operationsLoading.set(false);
        },
      });
  }
}
