import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIcon } from '@angular/material/icon';
import { AccountDto } from '../../../core/api/models';
import { EnumLabelPipe, IbanPipe, MoneyPipe } from '../../pipes';

/**
 * An account as a bank card (docs/07 §13.3, US-06): name, status, type, full IBAN, balance.
 * Opens the account details.
 */
@Component({
  selector: 'app-account-card',
  imports: [RouterLink, MatIcon, EnumLabelPipe, IbanPipe, MoneyPipe],
  templateUrl: './account-card.html',
  styleUrl: './account-card.scss',
})
export class AccountCard {
  readonly account = input.required<AccountDto>();
  /** Graphite instead of black: for every active account after the first one. */
  readonly alt = input(false);

  protected readonly active = computed(() => this.account().status === 'ACTIVE');
}
