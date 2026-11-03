import { Pipe, PipeTransform } from '@angular/core';

/** Splits an IBAN into groups of 4: `PL45 9990 0012 ...`. For display only. */
@Pipe({ name: 'iban' })
export class IbanPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const compact = value.replace(/\s+/g, '').toUpperCase();
    return compact.match(/.{1,4}/g)?.join(' ') ?? '';
  }
}
