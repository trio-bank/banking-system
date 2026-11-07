import {
  ERROR_MESSAGES,
  errorMessage,
  FALLBACK_MESSAGE,
  fieldErrorMessage,
} from './error-messages';

describe('error messages', () => {
  it('has a text for every error code from docs/05 §4.1', () => {
    const codes = [
      'VALIDATION_ERROR',
      'MALFORMED_REQUEST',
      'IDEMPOTENCY_KEY_MISSING',
      'UNAUTHENTICATED',
      'INVALID_CREDENTIALS',
      'USER_LOCKED',
      'USER_BLOCKED',
      'ACCESS_DENIED',
      'RESOURCE_NOT_FOUND',
      'EMAIL_ALREADY_REGISTERED',
      'PESEL_ALREADY_REGISTERED',
      'IDEMPOTENCY_KEY_REUSED',
      'CONCURRENT_MODIFICATION',
      'INVALID_STATE_TRANSITION',
      'CUSTOMER_UNDERAGE',
      'PRODUCT_NOT_AVAILABLE',
      'ACCOUNT_LIMIT_REACHED',
      'PREREQUISITE_ACCOUNT_REQUIRED',
      'INSUFFICIENT_FUNDS',
      'SOURCE_ACCOUNT_UNAVAILABLE',
      'RECIPIENT_ACCOUNT_UNAVAILABLE',
      'RECIPIENT_ACCOUNT_NOT_FOUND',
      'EXTERNAL_TRANSFER_NOT_SUPPORTED',
      'SAME_ACCOUNT_TRANSFER',
      'TRANSFER_POLICY_VIOLATION',
      'CURRENCY_NOT_SUPPORTED',
      'CASH_AMOUNT_INVALID',
      'OPERATION_NOT_ALLOWED_FOR_ACCOUNT',
      'SELF_ACTION_FORBIDDEN',
      'FEATURE_DISABLED',
      'INTERNAL_ERROR',
    ];
    const missing = codes.filter((code) => !ERROR_MESSAGES[code]);
    expect(missing).toEqual([]);
  });

  it('returns the text for a known code', () => {
    expect(errorMessage('INVALID_CREDENTIALS')).toBe('Invalid email or password.');
    expect(fieldErrorMessage('REQUIRED')).toBe('This field is required.');
  });

  it('returns the fallback for an unknown code', () => {
    expect(errorMessage('SOMETHING_NEW')).toBe(FALLBACK_MESSAGE);
    expect(fieldErrorMessage('SOMETHING_NEW')).toBe(FALLBACK_MESSAGE);
  });
});
