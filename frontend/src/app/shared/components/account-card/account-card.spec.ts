import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccountDto } from '../../../core/api/models';
import { mockAccounts, resetMockBank } from '../../../../testing/mock-bank';
import { formatMoney } from '../../pipes';
import { AccountCard } from './account-card';

resetMockBank();
const [PERSONAL] = mockAccounts();

describe('AccountCard', () => {
  const render = (changes: Partial<AccountDto> = {}, alt = false) => {
    const fixture = TestBed.createComponent(AccountCard);
    fixture.componentRef.setInput('account', { ...PERSONAL, ...changes });
    fixture.componentRef.setInput('alt', alt);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('a')!;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('US-06 shows name, status, type, currency, full IBAN and balance; opens the details', () => {
    const card = render();
    // Only line breaks from the template are squeezed: money keeps its non-breaking spaces.
    const text = card.textContent?.replace(/[ \n]+/g, ' ') ?? '';

    expect(text).toContain(PERSONAL.name);
    expect(text).toContain('Active');
    expect(text).toContain(`Personal · ${PERSONAL.currency}`);
    expect(text).toContain('PL45 9990 0012 1000 0000 0000 0001');
    expect(text).toContain(formatMoney(PERSONAL.balance, PERSONAL.currency));
    expect(card.getAttribute('href')).toBe(`/app/accounts/${PERSONAL.id}`);
  });

  it('active: black or graphite, with the yellow chip', () => {
    expect(render().className).not.toContain('alt');
    expect(render({}, true).classList).toContain('alt');
    expect(render().querySelector('.chip')).not.toBeNull();
  });

  it('blocked: grey with a lock, never graphite', () => {
    const card = render({ status: 'BLOCKED' }, true);

    expect(card.classList).toContain('inactive');
    expect(card.classList).not.toContain('alt');
    expect(card.querySelector('mat-icon')?.textContent).toBe('lock');
    expect(card.querySelector('.chip')).toBeNull();
    expect(card.querySelector('.status')?.textContent).toContain('Blocked');
  });

  it('closed: grey and dashed, no lock', () => {
    const card = render({ status: 'CLOSED' });

    expect(card.classList).toContain('inactive');
    expect(card.classList).toContain('closed');
    expect(card.querySelector('mat-icon')).toBeNull();
  });

  it('keeps a long name in the title so the cut-off part can be read', () => {
    const name = 'Holiday savings for the trip to Lisbon in summer';

    expect(render({ name }).querySelector('.name')?.getAttribute('title')).toBe(name);
  });
});
