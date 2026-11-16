import { HttpErrorResponse, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { CashOperationRequest, ProblemDetail, TransactionDto } from '../models';
import { atmDeposit, atmWithdrawal, resetAtmMocks } from './atm.mocks';
import { accountEntries, findAccount, resetMockBank } from './mock-bank';
import { MockHandler, MockResult } from './mock-http';

const PERSONAL = '00000000-0000-4000-d000-000000000001'; // 10420.51 PLN
const SAVINGS = '00000000-0000-4000-d000-000000000002';
const BLOCKED = '00000000-0000-4000-d000-000000000003';

let keyCounter = 0;

function send(
  handler: MockHandler,
  body: Partial<CashOperationRequest>,
  key: string | null = `key-${++keyCounter}`,
): MockResult {
  const headers = key ? new HttpHeaders({ 'Idempotency-Key': key }) : new HttpHeaders();
  return handler(new HttpRequest('POST', '/api/v1/atm', body, { headers }), {});
}

const code = (result: MockResult) =>
  ((result as HttpErrorResponse).error as ProblemDetail | undefined)?.code;
const transaction = (result: MockResult) => (result as HttpResponse<TransactionDto>).body!;

describe('ATM mocks', () => {
  beforeEach(() => {
    resetMockBank();
    resetAtmMocks();
  });

  it('T3 deposit: 201, balance goes up, new entry is on top of the history', () => {
    const result = send(atmDeposit, { accountId: PERSONAL, amount: '200' });
    expect(result.status).toBe(201);
    expect(transaction(result)).toMatchObject({
      type: 'CASH_DEPOSIT',
      channel: 'ATM',
      amount: '200.00',
      title: 'ATM cash deposit',
      sender: { name: 'Trio Bank', iban: null, ownAccount: false },
      recipient: { iban: 'PL45999000121000000000000001', ownAccount: true },
    });
    expect(findAccount(PERSONAL)?.balance).toBe('10620.51');
    expect(accountEntries(PERSONAL)[0]).toMatchObject({
      direction: 'INCOMING',
      balanceAfter: '10620.51',
    });
  });

  it('T4 withdrawal: balance goes down', () => {
    send(atmWithdrawal, { accountId: PERSONAL, amount: '420.00' });
    expect(findAccount(PERSONAL)?.balance).toBe('10000.51');
  });

  it('same key and body again → 200 with the same transaction, booked once', () => {
    const first = send(atmDeposit, { accountId: PERSONAL, amount: '100' }, 'retry');
    const again = send(atmDeposit, { accountId: PERSONAL, amount: '100' }, 'retry');
    expect(again.status).toBe(200);
    expect(transaction(again).id).toBe(transaction(first).id);
    expect(findAccount(PERSONAL)?.balance).toBe('10520.51');
  });

  it('same key with another body → 409, no key → 400', () => {
    send(atmDeposit, { accountId: PERSONAL, amount: '100' }, 'reused');
    expect(code(send(atmDeposit, { accountId: PERSONAL, amount: '200' }, 'reused'))).toBe(
      'IDEMPOTENCY_KEY_REUSED',
    );
    expect(code(send(atmDeposit, { accountId: PERSONAL, amount: '100' }, null))).toBe(
      'IDEMPOTENCY_KEY_MISSING',
    );
  });

  it.each([
    ['deposit', atmDeposit, '15.00', 'CASH_AMOUNT_INVALID'], // not whole tens
    ['deposit', atmDeposit, '20010', 'CASH_AMOUNT_INVALID'],
    ['withdrawal', atmWithdrawal, '5010', 'CASH_AMOUNT_INVALID'],
    ['withdrawal', atmWithdrawal, '0', 'VALIDATION_ERROR'],
    ['deposit', atmDeposit, '10.555', 'VALIDATION_ERROR'],
  ])('%s %s → %s', (_, handler, amount, expected) => {
    expect(code(send(handler, { accountId: PERSONAL, amount }))).toBe(expected);
  });

  it('checks the account: savings, blocked, unknown, other currency', () => {
    expect(code(send(atmDeposit, { accountId: SAVINGS, amount: '100' }))).toBe(
      'OPERATION_NOT_ALLOWED_FOR_ACCOUNT',
    );
    expect(code(send(atmDeposit, { accountId: BLOCKED, amount: '100' }))).toBe(
      'RECIPIENT_ACCOUNT_UNAVAILABLE',
    );
    expect(code(send(atmWithdrawal, { accountId: BLOCKED, amount: '100' }))).toBe(
      'SOURCE_ACCOUNT_UNAVAILABLE',
    );
    expect(code(send(atmDeposit, { accountId: 'nope', amount: '100' }))).toBe('RESOURCE_NOT_FOUND');
    expect(code(send(atmDeposit, { accountId: PERSONAL, amount: '100', currency: 'EUR' }))).toBe(
      'CURRENCY_NOT_SUPPORTED',
    );
  });

  it('withdrawal above the balance → INSUFFICIENT_FUNDS, nothing is booked', () => {
    for (let i = 0; i < 2; i++) {
      send(atmWithdrawal, { accountId: PERSONAL, amount: '5000' });
    }
    // 420.51 left
    expect(code(send(atmWithdrawal, { accountId: PERSONAL, amount: '430' }))).toBe(
      'INSUFFICIENT_FUNDS',
    );
    expect(findAccount(PERSONAL)?.balance).toBe('420.51');
  });
});
