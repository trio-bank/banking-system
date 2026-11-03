import { EnumLabelPipe } from './enum-label.pipe';
import { IbanPipe } from './iban.pipe';
import { MoneyPipe } from './money.pipe';
import { SignedAmountPipe } from './signed-amount.pipe';

// Intl puts special spaces in numbers — turn them into normal ones for easier comparing.
const plain = (text: string) => text.replace(/\s/g, ' ');

describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  it('formats money in Polish style', () => {
    expect(plain(pipe.transform('1250.00'))).toBe('1 250,00 zł');
    expect(plain(pipe.transform('0.5'))).toBe('0,50 zł');
    expect(plain(pipe.transform('1000000.00'))).toBe('1 000 000,00 zł');
  });

  it('formats negative amounts', () => {
    expect(plain(pipe.transform('-150.00'))).toBe('-150,00 zł');
  });

  it('returns an empty string for null or empty', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('')).toBe('');
  });

  it('returns a non-numeric value unchanged', () => {
    expect(pipe.transform('n/a')).toBe('n/a');
  });
});

describe('SignedAmountPipe', () => {
  const pipe = new SignedAmountPipe();

  it('adds + for incoming and − for outgoing', () => {
    expect(plain(pipe.transform('150.00', 'INCOMING'))).toBe('+150,00 zł');
    expect(plain(pipe.transform('150.00', 'OUTGOING'))).toBe('−150,00 zł');
  });

  it('returns no sign without a direction, empty for null', () => {
    expect(plain(pipe.transform('150.00', null))).toBe('150,00 zł');
    expect(pipe.transform(null, 'INCOMING')).toBe('');
  });
});

describe('IbanPipe', () => {
  const pipe = new IbanPipe();

  it('groups by 4 characters', () => {
    expect(pipe.transform('PL45999000121000000000000001')).toBe(
      'PL45 9990 0012 1000 0000 0000 0001',
    );
  });

  it('normalizes existing spaces and case', () => {
    expect(pipe.transform('pl45 99900012 1000000000000001')).toBe(
      'PL45 9990 0012 1000 0000 0000 0001',
    );
  });

  it('returns an empty string for null or empty', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform('')).toBe('');
  });
});

describe('EnumLabelPipe', () => {
  const pipe = new EnumLabelPipe();

  it('maps known values to labels', () => {
    expect(pipe.transform('INTERNAL_TRANSFER')).toBe('Transfer');
    expect(pipe.transform('CASH_DEPOSIT')).toBe('Cash deposit');
  });

  it('shows unknown values as they are', () => {
    expect(pipe.transform('SOME_FUTURE_TYPE')).toBe('SOME_FUTURE_TYPE');
  });

  it('returns an empty string for null', () => {
    expect(pipe.transform(null)).toBe('');
  });
});
