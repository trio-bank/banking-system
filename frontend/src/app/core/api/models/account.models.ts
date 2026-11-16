import {
  AccountStatus,
  AccountType,
  Channel,
  Direction,
  IsoDate,
  IsoDateTime,
  Money,
  OutgoingTransferPolicy,
  TransactionStatus,
  TransactionType,
  Uuid,
} from './common.models';

// P1 GET /account-products
export interface AccountProductDto {
  id: Uuid;
  code: string; // "SAVINGS_STANDARD"
  name: string;
  description: string | null;
  type: AccountType;
  currency: string;
  interestRate: string; // "4.0000", percent per year
  outgoingTransferPolicy: OutgoingTransferPolicy;
  atmAllowed: boolean;
  maxAccountsPerCustomer: number;
  prerequisiteType: AccountType | null; // savings needs a personal account
}

// C1, C3
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
  accruedInterest: string | null; // null for non-savings; 6 decimals
  openedAt: IsoDateTime;
}

// C2 POST /accounts. Without a name the backend uses the product name.
export interface OpenAccountRequest {
  productCode: string;
  name?: string | null;
}

// C4 PATCH /accounts/{accountId}
export interface RenameAccountRequest {
  name: string;
}

// C5 GET /accounts/{accountId}/history
export interface AccountHistoryItemDto {
  entryId: Uuid;
  transactionId: Uuid;
  reference: string; // "TB202610240000000123"
  type: TransactionType;
  status: TransactionStatus;
  channel: Channel;
  direction: Direction;
  amount: Money;
  currency: string;
  balanceAfter: Money;
  title: string;
  counterpartyName: string; // "Trio Bank" for cash, interest and tax
  counterpartyIban: string | null; // null for Trio Bank
  createdAt: IsoDateTime;
  bookedAt: IsoDateTime | null;
}

/** Query params of C5. Not a DTO from §7, the table is in docs/05 C5. All are optional. */
export interface AccountHistoryQuery {
  from?: IsoDate; // inclusive
  to?: IsoDate; // inclusive
  direction?: Direction;
  type?: TransactionType[];
  page?: number; // 0-based
  size?: number; // default 20, max 100
}
