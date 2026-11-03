import { Pipe, PipeTransform } from '@angular/core';

/** Human-readable names for API enum values. Add new ones here. */
export const ENUM_LABELS: Readonly<Record<string, string>> = {
  // TransactionType
  OWN_TRANSFER: 'Own transfer',
  INTERNAL_TRANSFER: 'Transfer',
  CASH_DEPOSIT: 'Cash deposit',
  CASH_WITHDRAWAL: 'Cash withdrawal',
  INTEREST: 'Interest',
  TAX: 'Tax',
  // TransactionStatus
  PENDING: 'Pending',
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  // AccountType
  PERSONAL: 'Personal',
  SAVINGS: 'Savings',
  // AccountStatus / UserStatus
  ACTIVE: 'Active',
  BLOCKED: 'Blocked',
  CLOSED: 'Closed',
  LOCKED: 'Locked',
  PENDING_VERIFICATION: 'Pending verification',
  // Role
  CUSTOMER: 'Customer',
  ADMIN: 'Admin',
  // Channel
  WEB: 'Online',
  ATM: 'ATM',
  BRANCH: 'Branch',
  SYSTEM: 'System',
};

/** `INTERNAL_TRANSFER` → "Transfer". Unknown values are shown as is. */
@Pipe({ name: 'enumLabel' })
export class EnumLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    return ENUM_LABELS[value] ?? value;
  }
}
