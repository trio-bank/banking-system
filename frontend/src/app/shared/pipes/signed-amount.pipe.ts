import { Pipe, PipeTransform } from '@angular/core';
import { formatMoney } from './money.pipe';

export type Direction = 'INCOMING' | 'OUTGOING';

/** Money with a sign: incoming `+150,00 zł`, outgoing `−150,00 zł`. */
@Pipe({ name: 'signedAmount' })
export class SignedAmountPipe implements PipeTransform {
  transform(
    value: string | null | undefined,
    direction: Direction | null | undefined,
    currency = 'PLN',
  ): string {
    const formatted = formatMoney(value, currency);
    if (formatted === '' || !direction) {
      return formatted;
    }
    return `${direction === 'INCOMING' ? '+' : '−'}${formatted}`;
  }
}
