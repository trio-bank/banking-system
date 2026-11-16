import { Clipboard } from '@angular/cdk/clipboard';
import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, OnDestroy, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { Subscription } from 'rxjs';
import { AccountDto, ProblemDetail } from '../../../core/api/models';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { errorMessage } from '../../../core/errors/error-messages';
import { EnumLabelPipe, IbanPipe, MoneyPipe } from '../../../shared/pipes';

/** `"4.0000"` → `4,00%`. Keeps up to 4 decimals if the rate has them. */
export function formatRate(rate: string): string {
  const format = new Intl.NumberFormat('pl-PL', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  });
  return `${format.format(Number(rate))}%`;
}

// No mat-icon here on purpose: it moves MatIcon into the first-load bundle (+10 kB).
/** Account header and tabs (US-08). The history tab is filled in #104. */
@Component({
  selector: 'app-account-details-page',
  imports: [
    DatePipe,
    EnumLabelPipe,
    IbanPipe,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    MoneyPipe,
    RouterLink,
  ],
  templateUrl: './account-details-page.html',
  styleUrl: './account-details-page.scss',
})
export class AccountDetailsPage implements OnDestroy {
  private readonly api = inject(AccountsApi);
  private readonly clipboard = inject(Clipboard);
  private readonly snackBar = inject(MatSnackBar);

  /** From the route `/app/accounts/:accountId`. */
  readonly accountId = input.required<string>();

  protected readonly account = signal<AccountDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** 404: no such account, or it belongs to someone else (the backend doesn't say which). */
  protected readonly notFound = signal(false);
  protected readonly formatRate = formatRate;

  private request?: Subscription;

  constructor() {
    // Going from one account to another reuses this page, so load on every id change.
    effect(() => {
      const id = this.accountId();
      untracked(() => this.load(id));
    });
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }

  protected load(id = this.accountId()): void {
    this.request?.unsubscribe();
    this.account.set(null);
    this.loading.set(true);
    this.error.set(null);
    this.notFound.set(false);
    this.request = this.api.get(id).subscribe({
      next: (account) => {
        this.account.set(account);
        this.loading.set(false);
      },
      error: (problem: ProblemDetail) => {
        if (problem.code === 'RESOURCE_NOT_FOUND') {
          this.notFound.set(true);
        } else {
          this.error.set(errorMessage(problem.code));
        }
        this.loading.set(false);
      },
    });
  }

  /** Copies without spaces, so it can be pasted into any transfer form. */
  protected copyIban(iban: string): void {
    const copied = this.clipboard.copy(iban);
    this.snackBar.open(copied ? 'IBAN copied' : 'Could not copy the IBAN', undefined, {
      duration: 3000,
    });
  }
}
