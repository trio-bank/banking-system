import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import {
  AccountDto,
  AccountHistoryItemDto,
  AccountProductDto,
  Money,
  ProblemDetail,
} from '../../core/api/models';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { AuthStore } from '../../core/auth/auth.store';
import { errorMessage } from '../../core/errors/error-messages';
import { cannotOpenReason } from '../accounts/account-rules';
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

/** A row of "Last operations": the entry and the account the row opens. */
export interface RecentOperation {
  entry: AccountHistoryItemDto;
  account: AccountDto;
  /** Set when money moved between two of the customer's own accounts: shown once, from → to. */
  transfer: { from: AccountDto; to: AccountDto } | null;
}

/**
 * The newest operations of all accounts together. `histories[i]` is the history of
 * `accounts[i]`. A transfer between own accounts is in both histories, so it's kept once:
 * the counterparty IBAN tells it's ours, and the row opens the account the money left.
 */
export function recentOperations(
  accounts: readonly AccountDto[],
  histories: readonly AccountHistoryItemDto[][],
  limit: number,
): RecentOperation[] {
  const byIban = new Map(accounts.map((account) => [account.iban, account]));
  const entries = accounts
    .flatMap((account, i) => (histories[i] ?? []).map((entry) => ({ entry, account })))
    .sort((a, b) => b.entry.createdAt.localeCompare(a.entry.createdAt));

  const seen = new Set<string>();
  const rows: RecentOperation[] = [];
  for (const { entry, account } of entries) {
    if (rows.length === limit) break;
    if (seen.has(entry.transactionId)) continue;
    seen.add(entry.transactionId);

    const other = entry.counterpartyIban ? byIban.get(entry.counterpartyIban) : undefined;
    const transfer = other
      ? entry.direction === 'OUTGOING'
        ? { from: account, to: other }
        : { from: other, to: account }
      : null;
    rows.push({ entry, account: transfer?.from ?? account, transfer });
  }
  return rows;
}

/**
 * Customer home: yellow header with greeting and total, account cards over its edge,
 * the last operations of all accounts (US-06, docs/07 §13).
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
  /**
   * False when every product is at its limit (or not allowed): then "Open account" is hidden.
   * Until the products load, or if they fail, the card stays: the backend checks anyway.
   */
  protected readonly canOpenMore = computed(() => {
    const products = this.products();
    const accounts = this.accounts();
    if (!products?.length || !accounts) {
      return true;
    }
    return products.some((product) => cannotOpenReason(product, accounts) === null);
  });
  private readonly products = signal<AccountProductDto[] | null>(null);

  // The operations have their own states: if they fail, the accounts are still shown.
  protected readonly operations = signal<RecentOperation[] | null>(null);
  protected readonly operationsLoading = signal(false);
  protected readonly operationsError = signal<string | null>(null);

  protected readonly operationIcon = operationIcon;
  /** Placeholders while loading: as many as a typical customer has. */
  protected readonly skeletonCards = [1, 2, 3];
  protected readonly skeletonRows = [1, 2, 3];

  constructor() {
    this.load();
    this.loadProducts();
  }

  /** Only for the limits; no error state, the "Open account" card just stays. */
  private loadProducts(): void {
    this.api
      .products()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (products) => this.products.set(products), error: () => undefined });
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

  /** The newest few of every account (at most 4 accounts), merged here. */
  protected loadOperations(): void {
    const accounts = this.accounts() ?? [];
    if (accounts.length === 0) {
      return;
    }
    this.operationsLoading.set(true);
    this.operationsError.set(null);
    forkJoin(accounts.map((a) => this.api.history(a.id, { size: LAST_OPERATIONS })))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pages) => {
          const histories = pages.map((page) => page.content);
          this.operations.set(recentOperations(accounts, histories, LAST_OPERATIONS));
          this.operationsLoading.set(false);
        },
        error: (problem: ProblemDetail) => {
          this.operationsError.set(errorMessage(problem.code));
          this.operationsLoading.set(false);
        },
      });
  }
}
