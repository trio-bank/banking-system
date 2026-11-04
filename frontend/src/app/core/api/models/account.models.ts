import { AccountStatus, AccountType, IsoDateTime, Money, Uuid } from './common.models';

export interface AccountDto {
  id: Uuid;
  iban: string; // without spaces
  name: string;
  type: AccountType;
  productCode: string;
  productName: string;
  currency: string;
  balance: Money;
  status: AccountStatus;
  interestRate: string;
  accruedInterest: string | null; // null for non-savings
  openedAt: IsoDateTime;
}
