import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { CurrentUserDto, LoginResponse, ProblemDetail } from '../../../core/api/models';
import { MOCK_USERS } from '../../../core/api/mocks/mock-data';
import { AuthApi } from '../../../core/api/services/auth-api';
import { AuthStore } from '../../../core/auth/auth.store';
import { LoginPage } from './login-page';

@Component({ template: '' })
class EmptyPage {}

const [CUSTOMER, ADMIN] = MOCK_USERS;

const CREDENTIALS = { email: 'anna.nowak@example.com', password: 'Str0ng!Passw0rd' };

const responseFor = (user: CurrentUserDto): LoginResponse => ({
  accessToken: 'token',
  tokenType: 'Bearer',
  expiresIn: 1800,
  expiresAt: '2099-01-01T00:00:00Z',
  user: { ...user, customer: null },
});

const problemOf = (
  status: number,
  code: string,
  extra: Partial<ProblemDetail> = {},
): ProblemDetail => ({
  type: 'about:blank',
  title: code,
  status,
  detail: '',
  instance: '/auth/login',
  code,
  traceId: 'trace-1',
  timestamp: '2026-11-07T12:00:00Z',
  errors: [],
  ...extra,
});

describe('LoginPage', () => {
  const login = vi.fn();
  const me = vi.fn();
  let harness: RouterTestingHarness;
  let page: LoginPage;

  const open = async (url = '/login') => {
    harness = await RouterTestingHarness.create();
    page = await harness.navigateByUrl(url, LoginPage);
  };

  beforeEach(() => {
    sessionStorage.clear();
    login.mockReset();
    me.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: LoginPage },
          { path: '**', component: EmptyPage },
        ]),
        { provide: AuthApi, useValue: { login, me } },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    sessionStorage.clear();
  });

  const element = () => harness.routeNativeElement as HTMLElement;
  const text = () => element().textContent ?? '';
  const button = () => element().querySelector<HTMLButtonElement>('button[type="submit"]');

  const render = async () => {
    harness.detectChanges();
    await harness.fixture.whenStable();
  };

  const submit = async (credentials = CREDENTIALS) => {
    page.form.setValue(credentials);
    element()
      .querySelector('form')
      ?.dispatchEvent(new Event('submit', { cancelable: true }));
    await render();
  };

  describe('form', () => {
    it('sends nothing while the fields are empty and shows the errors', async () => {
      await open();
      await submit({ email: '', password: '' });

      expect(login).not.toHaveBeenCalled();
      expect(text()).toContain('This field is required.');
    });

    it('rejects a malformed email without calling the API', async () => {
      await open();
      await submit({ email: 'not-an-email', password: 'x' });

      expect(login).not.toHaveBeenCalled();
      expect(text()).toContain('Enter a valid email address.');
    });

    it('does not apply the strength rules to the password', async () => {
      login.mockReturnValue(throwError(() => problemOf(401, 'INVALID_CREDENTIALS')));
      await open();
      await submit({ email: CREDENTIALS.email, password: 'x' });

      expect(login).toHaveBeenCalledWith({ email: CREDENTIALS.email, password: 'x' });
    });

    it('has a link to the register page', async () => {
      await open();
      expect(element().querySelector('a[href="/register"]')).not.toBeNull();
    });

    it('sends only one request on a double click and blocks the button meanwhile', async () => {
      login.mockReturnValue(new Subject());
      await open();
      await submit();
      expect(button()?.disabled).toBe(true);

      element()
        .querySelector('form')
        ?.dispatchEvent(new Event('submit', { cancelable: true }));
      expect(login).toHaveBeenCalledTimes(1);
    });
  });

  describe('successful login', () => {
    it.each([
      ['customer', CUSTOMER, '/app/dashboard'],
      ['admin', ADMIN, '/admin/customers'],
    ])(
      'TC-07 %s: saves the session, loads the profile and opens the home page',
      async (_, user, url) => {
        login.mockReturnValue(of(responseFor(user)));
        me.mockReturnValue(of(user));
        await open();
        await submit();

        const store = TestBed.inject(AuthStore);
        expect(store.token()).toBe('token');
        expect(store.currentUser()).toEqual(user);
        expect(TestBed.inject(Router).url).toBe(url);
      },
    );

    it('calls /me only after the token is saved', async () => {
      login.mockReturnValue(of(responseFor(CUSTOMER)));
      let tokenDuringMe: string | null = null;
      me.mockImplementation(() => {
        tokenDuringMe = TestBed.inject(AuthStore).token();
        return of(CUSTOMER);
      });
      await open();
      await submit();

      expect(tokenDuringMe).toBe('token');
    });

    it('drops the session and shows an error when /me fails', async () => {
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      login.mockReturnValue(of(responseFor(CUSTOMER)));
      me.mockReturnValue(throwError(() => problemOf(500, 'INTERNAL_ERROR')));
      await open();
      await submit();

      expect(TestBed.inject(AuthStore).token()).toBeNull();
      expect(TestBed.inject(Router).url).toBe('/login');
      expect(snackBar).toHaveBeenCalled();
    });
  });

  describe('login errors', () => {
    it('TC-08 shows the same message for a wrong password and an unknown email', async () => {
      login.mockReturnValue(throwError(() => problemOf(401, 'INVALID_CREDENTIALS')));
      await open();
      await submit();

      expect(element().querySelector('.banner')?.textContent).toContain(
        'Invalid email or password.',
      );
      expect(button()?.disabled).toBe(false);
    });

    it('TC-10 tells a blocked user to contact the bank', async () => {
      login.mockReturnValue(throwError(() => problemOf(403, 'USER_BLOCKED')));
      await open();
      await submit();

      expect(element().querySelector('.banner')?.textContent).toContain(
        'Your account is blocked. Please contact the bank.',
      );
    });

    it('shows a server error in a snackbar with the error id', async () => {
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      login.mockReturnValue(throwError(() => problemOf(500, 'INTERNAL_ERROR')));
      await open();
      await submit();

      expect(snackBar).toHaveBeenCalledWith(
        'Something went wrong. Please try again. Error ID: trace-1',
        'Close',
      );
    });

    it('removes the old error when trying again', async () => {
      login.mockReturnValueOnce(throwError(() => problemOf(401, 'INVALID_CREDENTIALS')));
      await open();
      await submit();

      login.mockReturnValueOnce(new Subject());
      await submit();
      expect(element().querySelector('.banner')).toBeNull();
    });
  });

  describe('TC-09 locked user', () => {
    const START = new Date('2026-11-07T12:00:00Z');
    const lockedProblem = problemOf(403, 'USER_LOCKED', {
      lockedUntil: '2026-11-07T12:15:00Z',
    });

    const openLocked = async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'], now: START });
      login.mockReturnValue(throwError(() => lockedProblem));
      await open();
      await submit();
    };

    it('shows when the user can try again and blocks the button', async () => {
      await openLocked();

      expect(text()).toContain('Too many failed login attempts');
      expect(text()).toContain('Try again in 15:00');
      expect(button()?.disabled).toBe(true);
    });

    it('counts down every second', async () => {
      await openLocked();

      vi.advanceTimersByTime(28_000);
      await render();
      expect(text()).toContain('Try again in 14:32');
    });

    it('allows logging in again when the time is over', async () => {
      await openLocked();

      vi.advanceTimersByTime(15 * 60 * 1000);
      await render();
      expect(text()).not.toContain('Try again');
      expect(element().querySelector('.banner')).toBeNull();
      expect(button()?.disabled).toBe(false);
    });

    it('does not block another user: changing the email removes the lock', async () => {
      await openLocked();

      page.form.controls.email.setValue('other@example.com');
      await render();
      expect(element().querySelector('.banner')).toBeNull();
      expect(button()?.disabled).toBe(false);
    });

    it('stops the countdown when the page is closed', async () => {
      await openLocked();
      const clearSpy = vi.spyOn(globalThis, 'clearInterval');

      await TestBed.inject(Router).navigateByUrl('/somewhere-else');
      expect(clearSpy).toHaveBeenCalled();
    });

    it('still shows the message when the server sends no time', async () => {
      login.mockReturnValue(throwError(() => problemOf(403, 'USER_LOCKED')));
      await open();
      await submit();

      expect(text()).toContain('Too many failed login attempts');
      expect(text()).not.toContain('Try again');
      expect(button()?.disabled).toBe(false);
    });
  });

  describe('reason in the URL', () => {
    it('TC-12 explains an expired session', async () => {
      await open('/login?reason=expired');
      expect(element().querySelector('.notice')?.textContent).toContain(
        'Your session has expired. Please log in again.',
      );
    });

    it('explains a logout because of inactivity', async () => {
      await open('/login?reason=inactive');
      expect(element().querySelector('.notice')?.textContent).toContain('inactivity');
    });

    it.each(['/login', '/login?reason=whatever'])('shows nothing for %s', async (url) => {
      await open(url);
      expect(element().querySelector('.notice')).toBeNull();
    });

    it('hides the message after pressing Log in', async () => {
      login.mockReturnValue(new Subject());
      await open('/login?reason=expired');
      await submit();
      expect(element().querySelector('.notice')).toBeNull();
    });
  });
});
