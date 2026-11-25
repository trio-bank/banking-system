import { Clipboard } from '@angular/cdk/clipboard';
import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import {
  afterNextRender,
  Component,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  OnDestroy,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { map, Subscription } from 'rxjs';
import { AccountDto, ProblemDetail } from '../../../core/api/models';
import { AccountsApi } from '../../../core/api/services/accounts-api';
import { errorMessage, fieldErrorMessage } from '../../../core/errors/error-messages';
import { PageHero } from '../../../shared/components/page-hero/page-hero';
import { EnumLabelPipe, IbanPipe, MoneyPipe } from '../../../shared/pipes';
import { AccountHistory } from '../../history/account-history/account-history';
import { ACCOUNT_NAME_MAX, accountNameValidator, formatRate } from '../account-rules';

/** Account header with inline rename (US-08, US-09) and the history tab (US-12). */
@Component({
  selector: 'app-account-details-page',
  imports: [
    AccountHistory,
    DatePipe,
    EnumLabelPipe,
    IbanPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIcon,
    MatInputModule,
    MoneyPipe,
    NgTemplateOutlet,
    PageHero,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './account-details-page.html',
  styleUrl: './account-details-page.scss',
})
export class AccountDetailsPage implements OnDestroy {
  private readonly api = inject(AccountsApi);
  private readonly clipboard = inject(Clipboard);
  private readonly snackBar = inject(MatSnackBar);
  private readonly injector = inject(Injector);

  /** From the route `/app/accounts/:accountId`. */
  readonly accountId = input.required<string>();

  protected readonly account = signal<AccountDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** 404: no such account, or it belongs to someone else (the backend doesn't say which). */
  protected readonly notFound = signal(false);
  /** ≥ 768px: product and dates are tiles in the header; on phones a card under it. */
  protected readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe('(min-width: 768px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly formatRate = formatRate;
  protected readonly nameMax = ACCOUNT_NAME_MAX;

  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly nameControl = new FormControl('', {
    nonNullable: true,
    validators: accountNameValidator,
  });
  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');

  private request?: Subscription;
  private renameRequest?: Subscription;

  constructor() {
    // Going from one account to another reuses this page, so load on every id change.
    effect(() => {
      const id = this.accountId();
      untracked(() => this.load(id));
    });
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
    this.renameRequest?.unsubscribe();
  }

  protected load(id = this.accountId()): void {
    this.request?.unsubscribe();
    this.stopEditing();
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

  protected startRename(account: AccountDto): void {
    this.nameControl.reset(account.name);
    this.editing.set(true);
    afterNextRender(() => this.nameInput()?.nativeElement.select(), { injector: this.injector });
  }

  protected stopEditing(): void {
    this.renameRequest?.unsubscribe();
    this.editing.set(false);
    this.saving.set(false);
  }

  protected saveName(event?: Event): void {
    event?.preventDefault(); // Enter submits the form; don't reload the page
    const account = this.account();
    this.nameControl.markAsTouched();
    this.nameControl.updateValueAndValidity(); // drops the last server error, so Save can retry
    if (!account || this.saving() || this.nameControl.invalid) {
      return;
    }
    const name = this.nameControl.value.trim();
    if (name === account.name) {
      this.stopEditing();
      return;
    }
    this.saving.set(true);
    this.renameRequest = this.api.rename(account.id, { name }).subscribe({
      next: (renamed) => {
        this.account.set(renamed);
        this.stopEditing();
        this.snackBar.open('Account renamed', undefined, { duration: 3000 });
      },
      error: (problem: ProblemDetail) => {
        this.saving.set(false);
        // Under the field, so the user can fix the name or cancel.
        const fieldError = problem.errors.find((e) => e.field === 'name');
        this.nameControl.setErrors({
          server: fieldError ? fieldErrorMessage(fieldError.code) : errorMessage(problem.code),
        });
      },
    });
  }

  protected nameError(): string {
    const errors = this.nameControl.errors ?? {};
    if (errors['server']) return errors['server'] as string;
    if (errors['required']) return fieldErrorMessage('REQUIRED');
    if (errors['maxlength']) return `At most ${ACCOUNT_NAME_MAX} characters.`;
    return '';
  }
}
