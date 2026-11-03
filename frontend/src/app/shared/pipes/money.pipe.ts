import { Pipe, PipeTransform } from '@angular/core';

/** `"1250.00"` → `1 250,00 zł`. For display only, don't calculate with it. */
export function formatMoney(value: string | null | undefined, currency = 'PLN'): string {
  if (value === null || value === undefined || value.trim() === '') {
    return '';
  }
  const amount = Number(value);
  if (!Number.isFinite(amount)) {
    return value;
  }
  // Without 'always' Polish format skips the space in 4-digit numbers: 1250,00 zł.
  // TypeScript doesn't know this option yet, so we cast.
  const options = { style: 'currency', currency, useGrouping: 'always' } as unknown;
  return new Intl.NumberFormat('pl-PL', options as Intl.NumberFormatOptions).format(amount);
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: string | null | undefined, currency = 'PLN'): string {
    return formatMoney(value, currency);
  }
}
