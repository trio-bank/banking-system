import {
  Channel,
  IsoDateTime,
  Money,
  TransactionStatus,
  TransactionType,
  Uuid,
} from './common.models';

// T3 POST /atm/deposits, T4 POST /atm/withdrawals. Currency: leave it out, PLN by default.
export interface CashOperationRequest {
  accountId: Uuid;
  amount: Money;
  currency?: string | null;
}

export interface PartyDto {
  name: string;
  iban: string | null; // null for Trio Bank (cash, interest, tax)
  ownAccount: boolean; // the account belongs to the current customer
}

export interface TransactionDto {
  id: Uuid;
  reference: string;
  type: TransactionType;
  status: TransactionStatus;
  channel: Channel;
  amount: Money;
  currency: string;
  title: string;
  sender: PartyDto;
  recipient: PartyDto;
  createdAt: IsoDateTime;
  bookedAt: IsoDateTime | null;
}
