import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { CurrentUserDto, ProblemDetail } from '../../core/api/models';
import { MOCK_USERS } from '../../core/api/mocks/mock-data';
import { AuthApi } from '../../core/api/services/auth-api';
import { AuthStore } from '../../core/auth/auth.store';
import { formatAddress, ProfilePage } from './profile-page';

const [ANNA] = MOCK_USERS;

const networkError: ProblemDetail = {
  type: 'about:blank',
  title: 'Network error',
  status: 0,
  detail: '',
  instance: '/me',
  code: 'NETWORK_ERROR',
  traceId: '',
  timestamp: '2026-11-13T12:00:00Z',
  errors: [],
};

describe('ProfilePage', () => {
  const me = vi.fn();

  const open = async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/app/profile', ProfilePage);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness;
  };

  beforeEach(() => {
    me.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'app/profile', component: ProfilePage }]),
        { provide: AuthApi, useValue: { me } },
      ],
    });
  });

  it('TC-14 shows all customer fields with the masked PESEL', async () => {
    me.mockReturnValue(of(ANNA));
    const text = (await open()).routeNativeElement?.textContent ?? '';
    const c = ANNA.customer!;

    expect(text).toContain(c.customerNumber);
    expect(text).toContain(`${c.firstName} ${c.lastName}`);
    expect(text).toContain('*******2343');
    expect(text).not.toContain('90051412343');
    expect(text).toContain('14.05.1990');
    expect(text).toContain(c.email);
    expect(text).toContain(c.phoneNumber);
    expect(text).toContain('Krakowska 12A/4, 45-018 Opole');
  });

  it('puts the loaded user into the store', async () => {
    me.mockReturnValue(of(ANNA));
    await open();

    expect(TestBed.inject(AuthStore).currentUser()).toEqual(ANNA);
  });

  it('shows a loader while waiting', async () => {
    me.mockReturnValue(new Subject<CurrentUserDto>());
    const el = (await open()).routeNativeElement;

    expect(el?.querySelector('mat-spinner')).not.toBeNull();
  });

  it('shows an error and loads again on "Try again"', async () => {
    me.mockReturnValueOnce(throwError(() => networkError)).mockReturnValueOnce(of(ANNA));
    const harness = await open();
    const el = harness.routeNativeElement!;

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Cannot reach the server');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    harness.detectChanges();

    expect(me).toHaveBeenCalledTimes(2);
    expect(el.textContent).toContain(ANNA.customer!.customerNumber);
  });
});

describe('formatAddress', () => {
  const base = {
    street: 'Długa',
    buildingNumber: '21',
    postalCode: '31-147',
    city: 'Kraków',
    countryCode: 'PL',
  };

  it('adds the apartment after a slash', () => {
    expect(formatAddress({ ...base, apartmentNumber: '3' })).toBe('Długa 21/3, 31-147 Kraków');
  });

  it('works without an apartment', () => {
    expect(formatAddress({ ...base, apartmentNumber: null })).toBe('Długa 21, 31-147 Kraków');
  });
});
