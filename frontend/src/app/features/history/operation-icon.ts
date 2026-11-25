import { AccountHistoryItemDto } from '../../core/api/models';

// Icon in the circle of an operation row. Unknown types fall back to the direction arrow.
const OPERATION_ICONS: Record<string, string> = {
  CASH_DEPOSIT: 'local_atm',
  CASH_WITHDRAWAL: 'local_atm',
  OWN_TRANSFER: 'sync_alt',
  INTERNAL_TRANSFER: 'swap_horiz',
  INTEREST: 'percent',
  TAX: 'account_balance',
};

/** Used by the dashboard's last operations and the account history. */
export function operationIcon(op: Pick<AccountHistoryItemDto, 'type' | 'direction'>): string {
  return OPERATION_ICONS[op.type] ?? (op.direction === 'INCOMING' ? 'south_west' : 'north_east');
}
