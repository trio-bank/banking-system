import {
  AccountDto,
  AccountHistoryItemDto,
  AccountProductDto,
  Channel,
  Direction,
  IsoDateTime,
  Money,
  PartyDto,
  TransactionDto,
  TransactionType,
  Uuid,
} from '../app/core/api/models';
import { MOCK_CUSTOMERS } from './mock-data';

// Anna's accounts and history for unit tests, built the way the backend books money,
// so balances and balanceAfter add up. Call resetMockBank() in beforeEach.

/** Products from docs/03 §9.1. */
export const MOCK_PRODUCTS: AccountProductDto[] = [
  {
    id: '00000000-0000-4000-9000-000000000001',
    code: 'PERSONAL_STANDARD',
    name: 'Trio Personal Account',
    description: 'Everyday account for payments, transfers and the ATM.',
    type: 'PERSONAL',
    currency: 'PLN',
    interestRate: '0.0000',
    outgoingTransferPolicy: 'ANY',
    atmAllowed: true,
    maxAccountsPerCustomer: 2,
    prerequisiteType: null,
  },
  {
    id: '00000000-0000-4000-9000-000000000002',
    code: 'SAVINGS_STANDARD',
    name: 'Trio Savings Account',
    description: 'Savings account with daily accrual and monthly capitalization.',
    type: 'SAVINGS',
    currency: 'PLN',
    interestRate: '4.0000',
    outgoingTransferPolicy: 'OWN_ACCOUNTS_ONLY',
    atmAllowed: false,
    maxAccountsPerCustomer: 2,
    prerequisiteType: 'PERSONAL',
  },
];

const [anna] = MOCK_CUSTOMERS;
const MOCK_OWNER_NAME = `${anna.firstName} ${anna.lastName}`;

/** Somebody outside our accounts: another customer or the bank itself. */
interface Outside {
  name: string;
  iban: string | null;
}
/** One side of a booking: our account or somebody outside. */
type Side = AccountDto | Outside;

interface Booking {
  type: TransactionType;
  channel: Channel;
  title: string;
  amount: Money;
  from: Side;
  to: Side;
  at: IsoDateTime;
}

interface Entry {
  accountId: Uuid;
  item: AccountHistoryItemDto;
}

let accounts: AccountDto[] = [];
let entries: Entry[] = [];
let accountSeq = 0;
let transactionSeq = 0;

export const mockAccounts = (): readonly AccountDto[] => accounts;

/** History of one account, newest first (the backend sorts by `createdAt,desc` too). */
export function accountEntries(accountId: Uuid): AccountHistoryItemDto[] {
  return entries
    .filter((entry) => entry.accountId === accountId)
    .map((entry) => entry.item)
    .reverse()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

function addAccount(product: AccountProductDto, name: string, openedAt: IsoDateTime): AccountDto {
  accountSeq++;
  const savings = product.type === 'SAVINGS';
  const account: AccountDto = {
    id: uuid('d000', accountSeq),
    iban: trioIban(accountSeq),
    name,
    type: product.type,
    productCode: product.code,
    productName: product.name,
    currency: product.currency,
    balance: '0.00',
    status: 'ACTIVE',
    interestRate: product.interestRate,
    accruedInterest: savings ? '0.000000' : null,
    openedAt,
  };
  accounts.push(account);
  return account;
}

/** Moves money and writes a history entry for each of our accounts in the booking. */
function book(booking: Booking): TransactionDto {
  transactionSeq++;
  const transaction: TransactionDto = {
    id: uuid('f000', transactionSeq),
    reference: `TB${booking.at.slice(0, 10).replaceAll('-', '')}${String(transactionSeq).padStart(10, '0')}`,
    type: booking.type,
    status: 'COMPLETED',
    channel: booking.channel,
    amount: formatGrosze(toGrosze(booking.amount)),
    currency: 'PLN',
    title: booking.title,
    sender: partyOf(booking.from),
    recipient: partyOf(booking.to),
    createdAt: booking.at,
    bookedAt: booking.at,
  };
  post(booking.from, 'OUTGOING', transaction, transaction.recipient);
  post(booking.to, 'INCOMING', transaction, transaction.sender);
  return transaction;
}

function post(side: Side, direction: Direction, transaction: TransactionDto, other: PartyDto) {
  if (!isAccount(side)) {
    return;
  }
  const sign = direction === 'INCOMING' ? 1 : -1;
  side.balance = formatGrosze(toGrosze(side.balance) + sign * toGrosze(transaction.amount));
  entries.push({
    accountId: side.id,
    item: {
      entryId: uuid('e000', entries.length + 1),
      transactionId: transaction.id,
      reference: transaction.reference,
      type: transaction.type,
      status: transaction.status,
      channel: transaction.channel,
      direction,
      amount: transaction.amount,
      currency: transaction.currency,
      balanceAfter: side.balance,
      title: transaction.title,
      counterpartyName: other.name,
      counterpartyIban: other.iban,
      createdAt: transaction.createdAt,
      bookedAt: transaction.bookedAt,
    },
  });
}

const isAccount = (side: Side): side is AccountDto => 'id' in side;

const partyOf = (side: Side): PartyDto =>
  isAccount(side)
    ? { name: MOCK_OWNER_NAME, iban: side.iban, ownAccount: true }
    : { ...side, ownAccount: false };

/** "1250.50" → 125050. Money is counted in whole grosze, so there are no rounding errors. */
const toGrosze = (amount: Money) => Math.round(Number(amount) * 100);

const formatGrosze = (grosze: number): Money =>
  `${Math.floor(grosze / 100)}.${String(grosze % 100).padStart(2, '0')}`;

const uuid = (group: string, n: number) =>
  `00000000-0000-4000-${group}-${String(n).padStart(12, '0')}`;

/** Trio Bank IBAN for account number n, with correct check digits (docs/03 §8.1). */
function trioIban(n: number): string {
  const bban = `999000121${String(n).padStart(15, '0')}`; // sort code + 16-digit number
  let remainder = 0;
  for (const digit of `${bban}252100`) {
    remainder = (remainder * 10 + Number(digit)) % 97;
  }
  return `PL${String(98 - remainder).padStart(2, '0')}${bban}`;
}

// ---------- Seed ----------

const TRIO_BANK: Outside = { name: 'Trio Bank', iban: null };
const JAN: Outside = { name: 'Jan Kowalski', iban: trioIban(900) };
const EWA: Outside = { name: 'Ewa Wiśniewska', iban: trioIban(901) };

/** Back to the start: 3 accounts with a few months of history. Call it in tests. */
export function resetMockBank(): void {
  accounts = [];
  entries = [];
  accountSeq = 0;
  transactionSeq = 0;

  const [personalProduct, savingsProduct] = MOCK_PRODUCTS;
  // 25 entries: more than one page of 20.
  const personal = addAccount(personalProduct, 'Trio Personal Account', '2026-08-03T09:00:00Z');
  const savings = addAccount(savingsProduct, 'Holiday savings', '2026-08-20T10:00:00Z');
  // Blocked by an admin, and it fills the limit of 2 personal accounts.
  const old = addAccount(personalProduct, 'Old personal account', '2026-09-01T08:00:00Z');

  const deposit = (to: AccountDto, amount: Money, at: string) =>
    book({
      type: 'CASH_DEPOSIT',
      channel: 'ATM',
      title: 'ATM cash deposit',
      amount,
      from: TRIO_BANK,
      to,
      at: `2026-${at}:00Z`,
    });
  const withdrawal = (from: AccountDto, amount: Money, at: string) =>
    book({
      type: 'CASH_WITHDRAWAL',
      channel: 'ATM',
      title: 'ATM cash withdrawal',
      amount,
      from,
      to: TRIO_BANK,
      at: `2026-${at}:00Z`,
    });
  const transfer = (from: Side, to: Side, amount: Money, title: string, at: string) =>
    book({
      type: 'INTERNAL_TRANSFER',
      channel: 'WEB',
      title,
      amount,
      from,
      to,
      at: `2026-${at}:00Z`,
    });
  const own = (from: AccountDto, to: AccountDto, amount: Money, at: string) =>
    book({
      type: 'OWN_TRANSFER',
      channel: 'WEB',
      title: 'Own transfer',
      amount,
      from,
      to,
      at: `2026-${at}:00Z`,
    });
  const interest = (gross: Money, tax: Money, month: string, at: string) => {
    book({
      type: 'INTEREST',
      channel: 'SYSTEM',
      title: `Interest capitalization ${month}`,
      amount: gross,
      from: TRIO_BANK,
      to: savings,
      at: `2026-${at}:00Z`,
    });
    book({
      type: 'TAX',
      channel: 'SYSTEM',
      title: `Interest tax 19% ${month}`,
      amount: tax,
      from: savings,
      to: TRIO_BANK,
      at: `2026-${at}:30Z`,
    });
  };

  deposit(personal, '3000.00', '08-03T09:15');
  transfer(EWA, personal, '4200.00', 'Salary July', '08-05T07:30');
  transfer(personal, JAN, '1500.00', 'Rent August', '08-07T18:20');
  withdrawal(personal, '200.00', '08-12T16:45');
  own(personal, savings, '2000.00', '08-20T10:05');
  transfer(personal, EWA, '89.99', 'Concert tickets', '08-25T20:10');

  deposit(old, '150.00', '09-01T08:10');
  interest('6.58', '1.25', '2026-08', '09-01T00:05');
  transfer(EWA, personal, '4200.00', 'Salary August', '09-04T07:30');
  transfer(personal, JAN, '1500.00', 'Rent September', '09-07T18:20');
  withdrawal(personal, '300.00', '09-10T12:00');
  transfer(personal, EWA, '45.50', 'Pizza', '09-15T19:40');
  own(personal, savings, '1000.00', '09-18T09:00');
  deposit(personal, '500.00', '09-22T14:25');
  transfer(JAN, personal, '120.00', 'Money back for groceries', '09-28T11:00');

  interest('13.97', '2.65', '2026-09', '10-01T00:05');
  withdrawal(old, '30.00', '10-03T17:00');
  transfer(EWA, personal, '4200.00', 'Salary September', '10-05T07:30');
  transfer(personal, JAN, '1500.00', 'Rent October', '10-07T18:20');
  withdrawal(personal, '400.00', '10-11T15:10');
  transfer(personal, EWA, '250.00', 'Birthday gift', '10-16T10:30');
  own(savings, personal, '500.00', '10-21T13:00');
  transfer(personal, JAN, '150.00', 'Dinner', '10-24T13:02');
  deposit(personal, '1000.00', '10-29T09:45');

  interest('10.27', '1.95', '2026-10', '11-01T00:05');
  transfer(EWA, personal, '4200.00', 'Salary October', '11-05T07:30');
  transfer(personal, JAN, '1500.00', 'Rent November', '11-07T18:20');
  withdrawal(personal, '200.00', '11-10T16:00');
  transfer(personal, EWA, '64.00', 'Cinema', '11-13T21:15');
  own(personal, savings, '800.00', '11-14T08:50');

  savings.accruedInterest = '5.465712';
  old.status = 'BLOCKED';
}

resetMockBank();
