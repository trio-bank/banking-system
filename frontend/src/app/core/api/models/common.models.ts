// API types shared by all endpoints. Copied from docs/05 §7 — keep them in sync.

export type Money = string; // "1250.00"
export type Uuid = string;
export type IsoDate = string; // "2026-10-24"
export type IsoDateTime = string; // "2026-10-24T13:02:11Z"

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Error body of every failed request. Show messages by `code`, not by `detail`. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  traceId: string;
  timestamp: IsoDateTime;
  errors: FieldError[];
  lockedUntil?: IsoDateTime; // only for USER_LOCKED
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export type Role = 'CUSTOMER' | 'ADMIN';
export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'LOCKED' | 'BLOCKED' | 'CLOSED';
export type AccountType = 'PERSONAL' | 'SAVINGS';
export type AccountStatus = 'ACTIVE' | 'BLOCKED' | 'CLOSED';
export type OutgoingTransferPolicy = 'ANY' | 'OWN_ACCOUNTS_ONLY';
// `string & {}` = any other string is OK too (new values may come), but the editor still
// suggests the known ones. Plain `| string` would swallow them.
export type TransactionType =
  | 'OWN_TRANSFER'
  | 'INTERNAL_TRANSFER'
  | 'CASH_DEPOSIT'
  | 'CASH_WITHDRAWAL'
  | 'INTEREST'
  | 'TAX'
  | (string & {});
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
export type Channel = 'WEB' | 'ATM' | 'BRANCH' | 'SYSTEM' | (string & {});
export type Direction = 'INCOMING' | 'OUTGOING';
