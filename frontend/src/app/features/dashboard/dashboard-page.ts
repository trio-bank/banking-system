import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AccountDto, AccountHistoryItemDto, Money, ProblemDetail } from '../../core/api/models';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { AuthStore } from '../../core/auth/auth.store';
import { errorMessage } from '../../core/errors/error-messages';
import { AccountCard } from '../../shared/components/account-card/account-card';
import { PageHero } from '../../shared/components/page-hero/page-hero';
import { MoneyPipe, SignedAmountPipe } from '../../shared/pipes';

const LAST_OPERATIONS = 5;

// Icon in the circle of an operation row. Unknown types fall back to the direction arrow.
const OPERATION_ICONS: Record<string, string> = {
  CASH_DEPOSIT: 'local_atm',
  CASH_WITHDRAWAL: 'local_atm',
  OWN_TRANSFER: 'sync_alt',
  INTERNAL_TRANSFER: 'swap_horiz',
  INTEREST: 'percent',
  TAX: 'account_balance',
};

export function operationIcon(op: Pick<AccountHistoryItemDto, 'type' | 'direction'>): string {
  return OPERATION_ICONS[op.type] ?? (op.direction === 'INCOMING' ? 'south_west' : 'north_east');
}

/** Sum of the active accounts (US-06). Counted in grosze, so there are no rounding errors. */
export function totalBalance(accounts: readonly AccountDto[]): Money {
  const grosze = accounts
    .filter((account) => account.status === 'ACTIVE')
    .reduce((sum, account) => sum + Math.round(Number(account.balance) * 100), 0);
  return `${Math.floor(grosze / 100)}.${String(grosze % 100).padStart(2, '0')}`;
}

/**
 * Customer home: yellow header with greeting and total, account cards over its edge,
 * the last operations of the first account (US-06, docs/07 §13).
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [
    AccountCard,
    DatePipe,
    MatButtonModule,
    MatIcon,
    MoneyPipe,
    PageHero,
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

  protected readonly operationIcon = operationIcon;
  /** Placeholders while loading: as many as a typical customer has. */
  protected readonly skeletonCards = [1, 2, 3];
  protected readonly skeletonRows = [1, 2, 3];

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
