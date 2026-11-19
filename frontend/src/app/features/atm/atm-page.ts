import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AccountDto, ProblemDetail } from '../../core/api/models';
import { AccountsApi } from '../../core/api/services/accounts-api';
import { errorMessage } from '../../core/errors/error-messages';
import { AtmKind, AtmOperation } from './atm-operation/atm-operation';

/** ATM (US-14, US-15): loads the accounts once, deposit and withdrawal share them. */
@Component({
  selector: 'app-atm-page',
  imports: [
    AtmOperation,
    MatButtonModule,
    MatButtonToggleModule,
    MatProgressSpinnerModule,
    RouterLink,
  ],
  templateUrl: './atm-page.html',
  styleUrl: './atm-page.scss',
})
export class AtmPage {
  private readonly api = inject(AccountsApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly accounts = signal<AccountDto[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly kind = signal<AtmKind>('deposit');

  /** BR-20: the ATM works with personal accounts only. */
  protected readonly personal = computed(
    () => this.accounts()?.filter((a) => a.type === 'PERSONAL') ?? [],
  );
  protected readonly hasActive = computed(() => this.personal().some((a) => a.status === 'ACTIVE'));

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
        },
        error: (problem: ProblemDetail) => {
          this.error.set(errorMessage(problem.code));
          this.loading.set(false);
        },
      });
  }

  /** New balance in the account list without loading it again. */
  protected replace(updated: AccountDto): void {
    this.accounts.update((list) => list?.map((a) => (a.id === updated.id ? updated : a)) ?? null);
  }
}
