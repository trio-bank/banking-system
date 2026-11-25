import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe, formatDate } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  input,
  LOCALE_ID,
  OnDestroy,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { map, Subscription } from 'rxjs';
import { AccountHistoryItemDto, PageResponse, ProblemDetail } from '../../../core/api/models';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { errorMessage } from '../../../core/errors/error-messages';
import { IbanPipe, MoneyPipe, SignedAmountPipe } from '../../../shared/pipes';
import { operationIcon } from '../operation-icon';

export const HISTORY_PAGE_SIZE = 20; // US-12
/** Below this width the table gets rows grouped by day instead (NFR-USE-01: usable from 360 px). */
export const PHONE_QUERY = '(max-width: 767.98px)';

/** Operations of one day on the phone list: "Today", "19 November", "3 March 2025". */
export interface HistoryDay {
  key: string;
  label: string;
  items: AccountHistoryItemDto[];
}

/** Groups a page by local day, keeping the order (newest first). */
export function groupByDay(
  items: readonly AccountHistoryItemDto[],
  now: Date,
  locale: string,
): HistoryDay[] {
  const dayKey = (date: Date | string) => formatDate(date, 'yyyy-MM-dd', locale);
  const today = dayKey(now);
  const year = now.getFullYear();
  const days: HistoryDay[] = [];
  for (const item of items) {
    const key = dayKey(item.createdAt);
    let day = days.at(-1);
    if (day?.key !== key) {
      const sameYear = new Date(item.createdAt).getFullYear() === year;
      const label =
        key === today
          ? 'Today'
          : formatDate(item.createdAt, sameYear ? 'd MMMM' : 'd MMMM y', locale);
      day = { key, label, items: [] };
      days.push(day);
    }
    day.items.push(item);
  }
  return days;
}

/** History tab of one account (US-12 basic): newest first, 20 per page. Filters come in Sprint 3. */
@Component({
  selector: 'app-account-history',
  imports: [
    DatePipe,
    IbanPipe,
    MatButtonModule,
    MatIcon,
    MatPaginatorModule,
    MatTableModule,
    MoneyPipe,
    SignedAmountPipe,
  ],
  templateUrl: './account-history.html',
  styleUrl: './account-history.scss',
})
export class AccountHistory implements OnDestroy {
  private readonly api = inject(AccountsApi);
  private readonly locale = inject(LOCALE_ID);

  readonly accountId = input.required<string>();

  // Four columns don't fit a phone, so there it is a list of rows, not a squeezed table.
  protected readonly phone = toSignal(
    inject(BreakpointObserver)
      .observe(PHONE_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly pageSize = HISTORY_PAGE_SIZE;
  protected readonly columns = ['date', 'description', 'amount', 'balanceAfter'];
  protected readonly icon = operationIcon;
  protected readonly skeletonRows = [1, 2, 3, 4, 5];

  protected readonly page = signal<PageResponse<AccountHistoryItemDto> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly days = computed(() =>
    groupByDay(this.page()?.content ?? [], new Date(), this.locale),
  );
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
