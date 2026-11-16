import { HttpRequest } from '@angular/common/http';
import { checkAmount, normalizeAmount } from '../../../shared/validators';
import { CashOperationRequest, TransactionDto } from '../models';
import { book, findAccount, toGrosze, TRIO_BANK } from './mock-bank';
import {
  isoNow,
  MockHandler,
  MockResult,
  notFound,
  ok,
  problem,
  validationError,
} from './mock-http';

// BR-19: the ATM works with banknotes only, so whole tens of PLN.
const LIMITS = {
  deposit: { min: 10, max: 20_000, step: 10 },
  withdrawal: { min: 10, max: 5_000, step: 10 },
};

type Kind = keyof typeof LIMITS;

/** Idempotency-Key → what was sent and what we answered. Same key again = same answer. */
const done = new Map<string, { body: string; transaction: TransactionDto }>();

/** For tests. */
export const resetAtmMocks = () => done.clear();

/** T3 */
export const atmDeposit: MockHandler = (req) => cash(req, 'deposit');

/** T4 */
export const atmWithdrawal: MockHandler = (req) => cash(req, 'withdrawal');

function cash(req: HttpRequest<unknown>, kind: Kind): MockResult {
  const key = req.headers.get('Idempotency-Key');
  if (!key) {
    return problem(
      req,
      400,
      'IDEMPOTENCY_KEY_MISSING',
      'Idempotency key missing',
      'Header Idempotency-Key is required.',
    );
  }
  const body = req.body as CashOperationRequest;
  const previous = done.get(key);
  if (previous) {
    // Retry after a lost answer: nothing is booked again, 200 with the first result.
    return previous.body === JSON.stringify(body)
      ? ok(req, previous.transaction)
      : problem(
          req,
          409,
          'IDEMPOTENCY_KEY_REUSED',
          'Idempotency key reused',
          'This key was already used for a different request.',
        );
  }

  const result = check(req, kind, body);
  if (result) {
    return result;
  }
  const account = findAccount(body.accountId)!;
  const amount = normalizeAmount(body.amount)!;
  const transaction =
    kind === 'deposit'
      ? book({
          type: 'CASH_DEPOSIT',
          channel: 'ATM',
          title: 'ATM cash deposit',
          amount,
          from: TRIO_BANK,
          to: account,
          at: isoNow(),
        })
      : book({
          type: 'CASH_WITHDRAWAL',
          channel: 'ATM',
          title: 'ATM cash withdrawal',
          amount,
          from: account,
          to: TRIO_BANK,
          at: isoNow(),
        });
  done.set(key, { body: JSON.stringify(body), transaction });
  return ok(req, transaction, 201, { Location: `/api/v1/transactions/${transaction.id}` });
}

/** The error for the first broken rule, or null when the operation can go. */
function check(
  req: HttpRequest<unknown>,
  kind: Kind,
  body: CashOperationRequest,
): MockResult | null {
  const format = checkAmount(String(body.amount ?? ''));
  if (format === 'INVALID_FORMAT' || format === 'TOO_MANY_DECIMALS') {
    return validationError(req, 'amount', format, 'Amount is invalid.');
  }
  if (format) {
    return validationError(req, 'amount', 'OUT_OF_RANGE', 'Amount must be 0.01–1000000.00.');
  }
  const account = findAccount(body.accountId);
  if (!account) {
    return notFound(req, 'Account');
  }
  if (body.currency && body.currency !== 'PLN') {
    return problem(
      req,
      422,
      'CURRENCY_NOT_SUPPORTED',
      'Currency not supported',
      'Only PLN is supported.',
    );
  }
  const { min, max } = LIMITS[kind];
  if (checkAmount(body.amount, LIMITS[kind])) {
    return problem(
      req,
      422,
      'CASH_AMOUNT_INVALID',
      'Cash amount invalid',
      `ATM ${kind}: ${min}–${max} PLN, in whole tens.`,
    );
  }
  if (account.type !== 'PERSONAL') {
    return problem(
      req,
      422,
      'OPERATION_NOT_ALLOWED_FOR_ACCOUNT',
      'Operation not allowed for account',
      'The ATM works only with personal accounts.',
    );
  }
  if (account.status !== 'ACTIVE') {
    return kind === 'deposit'
      ? problem(
          req,
          422,
          'RECIPIENT_ACCOUNT_UNAVAILABLE',
          'Recipient account unavailable',
          'The account is not active.',
        )
      : problem(
          req,
          422,
          'SOURCE_ACCOUNT_UNAVAILABLE',
          'Source account unavailable',
          'The account is not active.',
        );
  }
  if (kind === 'withdrawal' && toGrosze(account.balance) < toGrosze(body.amount)) {
    return problem(
      req,
      422,
      'INSUFFICIENT_FUNDS',
      'Insufficient funds',
      'The account balance is lower than the requested amount.',
    );
  }
  return null;
}
