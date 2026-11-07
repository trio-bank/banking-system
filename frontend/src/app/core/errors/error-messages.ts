// The only place where error codes become texts for users. Codes: docs/05 §4.

export const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Some fields are not filled in correctly.',
  MALFORMED_REQUEST: 'The request could not be processed. Please try again.',
  IDEMPOTENCY_KEY_MISSING: 'The request could not be processed. Please try again.',
  UNAUTHENTICATED: 'Your session has expired. Please log in again.',
  INVALID_CREDENTIALS: 'Invalid email or password.',
  USER_LOCKED: 'Too many failed login attempts. Your account is temporarily locked.',
  USER_BLOCKED: 'Your account is blocked. Please contact the bank.',
  ACCESS_DENIED: 'You do not have access to this page.',
  RESOURCE_NOT_FOUND: 'We could not find what you are looking for.',
  EMAIL_ALREADY_REGISTERED: 'This email is already registered.',
  PESEL_ALREADY_REGISTERED: 'A customer with this PESEL already exists.',
  IDEMPOTENCY_KEY_REUSED: 'This operation was already sent with different data.',
  CONCURRENT_MODIFICATION: 'The data was changed at the same time. Please try again.',
  INVALID_STATE_TRANSITION: 'This action is not possible in the current status.',
  CUSTOMER_UNDERAGE: 'You must be at least 18 years old to open an account.',
  PRODUCT_NOT_AVAILABLE: 'This product is not available right now.',
  ACCOUNT_LIMIT_REACHED: 'You already have the maximum number of accounts of this type.',
  PREREQUISITE_ACCOUNT_REQUIRED: 'You need an active personal account to open this account.',
  INSUFFICIENT_FUNDS: 'You do not have enough money on this account.',
  SOURCE_ACCOUNT_UNAVAILABLE: 'This account cannot send money right now.',
  RECIPIENT_ACCOUNT_UNAVAILABLE: 'The recipient account cannot receive money right now.',
  RECIPIENT_ACCOUNT_NOT_FOUND: 'No Trio Bank account exists with this number.',
  EXTERNAL_TRANSFER_NOT_SUPPORTED: 'Transfers to other banks are not available yet.',
  SAME_ACCOUNT_TRANSFER: 'You cannot send money to the same account.',
  TRANSFER_POLICY_VIOLATION: 'From this account you can only send money to your own accounts.',
  CURRENCY_NOT_SUPPORTED: 'This currency is not supported.',
  CASH_AMOUNT_INVALID: 'This amount cannot be paid in or out in cash.',
  OPERATION_NOT_ALLOWED_FOR_ACCOUNT: 'This operation is not allowed for this account.',
  SELF_ACTION_FORBIDDEN: 'You cannot do this to your own account.',
  FEATURE_DISABLED: 'This feature is not available.',
  INTERNAL_ERROR: 'Something went wrong. Please try again.',
  // Frontend only: no answer from the server at all.
  NETWORK_ERROR: 'Cannot reach the server. Check your connection and try again.',
};

/** For `errors[].code` under a form field. */
export const FIELD_ERROR_MESSAGES: Record<string, string> = {
  REQUIRED: 'This field is required.',
  INVALID_FORMAT: 'Wrong format.',
  TOO_SHORT: 'Too short.',
  TOO_LONG: 'Too long.',
  OUT_OF_RANGE: 'The value is out of range.',
  INVALID_CHECKSUM: 'The number is not valid.',
  WEAK_PASSWORD: 'The password is too weak.',
  TOO_MANY_DECIMALS: 'Use at most 2 decimal places.',
  INVALID_DATE: 'Wrong date.',
};

export const FALLBACK_MESSAGE = 'Something went wrong. Please try again.';

/** Unknown codes (e.g. a new one from the backend) get the fallback text. */
export function errorMessage(code: string): string {
  return ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE;
}

export function fieldErrorMessage(code: string): string {
  return FIELD_ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE;
}
