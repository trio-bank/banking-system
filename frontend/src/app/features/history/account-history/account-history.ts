import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, OnDestroy, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { map, Subscription } from 'rxjs';
import { AccountHistoryItemDto, PageResponse, ProblemDetail } from '../../../core/api/models';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { errorMessage } from '../../../core/errors/error-messages';
import { IbanPipe, MoneyPipe, SignedAmountPipe } from '../../../shared/pipes';

export const HISTORY_PAGE_SIZE = 20; // US-12
/** Below this width the table gets a list of cards instead (NFR-USE-01: usable from 360 px). */
export const PHONE_QUERY = '(max-width: 599.98px)';

/** History tab of one account (US-12 basic): newest first, 20 per page. Filters come in Sprint 3. */
@Component({
  selector: 'app-account-history',
  imports: [
    DatePipe,
    IbanPipe,
    MatButtonModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MoneyPipe,
    SignedAmountPipe,
  ],
  templateUrl: './account-history.html',
  styleUrl: './account-history.scss',
})
export class AccountHistory implements OnDestroy {
  private readonly api = inject(AccountsApi);

  readonly accountId = input.required<string>();

  // Five columns don't fit a phone, so there it is a list of cards, not a squeezed table.
  protected readonly phone = toSignal(
    inject(BreakpointObserver)
      .observe(PHONE_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly pageSize = HISTORY_PAGE_SIZE;
  protected readonly columns = ['date', 'counterparty', 'title', 'amount', 'balanceAfter'];

  protected readonly page = signal<PageResponse<AccountHistoryItemDto> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The page asked for last, so "Try again" asks for the same one. */
  private pageIndex = 0;
  private request?: Subscription;

  constructor() {
    // Another account → start again from the first page.
    effect(() => {
      const id = this.accountId();
      untracked(() => {
        this.page.set(null);
        this.load(id, 0);
      });
    });
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }

  protected load(id = this.accountId(), pageIndex = this.pageIndex): void {
    this.request?.unsubscribe();
    this.pageIndex = pageIndex;
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api.history(id, { page: pageIndex, size: HISTORY_PAGE_SIZE }).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (problem: ProblemDetail) => {
        this.error.set(errorMessage(problem.code));
        this.loading.set(false);
      },
    });
  }

  protected onPage(event: PageEvent): void {
    this.load(this.accountId(), event.pageIndex);
  }

  protected trackEntry = (_: number, item: AccountHistoryItemDto) => item.entryId;
}
