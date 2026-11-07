import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { LoginResponse } from '../api/models';
import { MOCK_USERS } from '../api/mocks/mock-data';
import { AuthApi } from '../api/services/auth-api';
import { AuthStore } from './auth.store';
import { SessionTimer } from './session-timer';
import { STAY_LOGGED_IN } from './session-warning-dialog';

@Component({ template: '' })
class EmptyPage {}

const NOW = new Date('2026-11-07T12:00:00Z');
const MINUTE = 60 * 1000;

const session = (expiresAt = '2099-01-01T00:00:00Z'): LoginResponse => ({
  accessToken: 'token',
  tokenType: 'Bearer',
  expiresIn: 1800,
  expiresAt,
  user: MOCK_USERS[0],
});

describe('SessionTimer', () => {
  const logout = vi.fn();
  const open = vi.fn();
  const close = vi.fn();
  let closed: Subject<string | undefined>;
  let store: AuthStore;
  const navigate = vi.fn();

  const pass = (ms: number) => vi.advanceTimersByTimeAsync(ms);

  const logIn = (expiresAt?: string) => {
    store.setSession(session(expiresAt));
    TestBed.tick(); // lets the timer see the login
  };

  /** The countdown value passed to the dialog. */
  const secondsLeft = () =>
    (open.mock.lastCall?.[1] as { data: { secondsLeft: () => number } }).data.secondsLeft();

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'], now: NOW });
    sessionStorage.clear();
    logout.mockReset().mockReturnValue(of(null));
    open.mockReset();
    close.mockReset();
    closed = new Subject();
    open.mockReturnValue({ close, afterClosed: () => closed });

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: EmptyPage }]),
        { provide: AuthApi, useValue: { logout } },
        { provide: MatDialog, useValue: { open } },
      ],
    });
    store = TestBed.inject(AuthStore);
    navigate.mockReset().mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockImplementation(navigate);
    TestBed.inject(SessionTimer);
    TestBed.tick();
  });

  afterEach(() => {
    store.clear();
    TestBed.tick();
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('does nothing while nobody is logged in', async () => {
    await pass(11 * MINUTE);
    expect(open).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  describe('inactivity', () => {
    beforeEach(() => logIn());

    it('shows no warning before 9 minutes', async () => {
      await pass(9 * MINUTE - 1000);
      expect(open).not.toHaveBeenCalled();
    });

    it('shows the warning once after 9 minutes, with a 60 s countdown', async () => {
      await pass(9 * MINUTE);
      expect(open).toHaveBeenCalledTimes(1);
      expect(secondsLeft()).toBe(60);

      await pass(15 * 1000);
      expect(open).toHaveBeenCalledTimes(1);
      expect(secondsLeft()).toBe(45);
    });

    it('cannot be closed by Esc or a click outside', async () => {
      await pass(9 * MINUTE);
      expect(open.mock.lastCall?.[1]).toMatchObject({ disableClose: true });
    });

    it('logs out after 10 minutes and tells the server', async () => {
      await pass(10 * MINUTE);

      expect(logout).toHaveBeenCalledTimes(1);
      expect(store.isLoggedIn()).toBe(false);
      expect(close).toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'inactive' } });
    });

    it('stays logged in with "Stay logged in", and counts from that moment', async () => {
      await pass(9 * MINUTE + 30 * 1000);
      closed.next(STAY_LOGGED_IN);

      await pass(9 * MINUTE - 1000);
      expect(store.isLoggedIn()).toBe(true);
      expect(open).toHaveBeenCalledTimes(1);

      await pass(1000);
      expect(open).toHaveBeenCalledTimes(2);
    });

    it('logs out even if the server does not answer', async () => {
      logout.mockReturnValue(new Subject());
      await pass(10 * MINUTE + 5000);

      expect(store.isLoggedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'inactive' } });
    });
  });

  describe('activity', () => {
    beforeEach(() => logIn());

    it.each(['click', 'keydown'])('%s starts the 9 minutes again', async (eventName) => {
      await pass(8 * MINUTE);
      document.dispatchEvent(new Event(eventName));

      await pass(9 * MINUTE - 1000);
      expect(open).not.toHaveBeenCalled();

      await pass(1000);
      expect(open).toHaveBeenCalledTimes(1);
    });

    it('moving to another page starts the 9 minutes again', async () => {
      await pass(8 * MINUTE);
      await TestBed.inject(Router).navigateByUrl('/another-page');

      await pass(9 * MINUTE - 1000);
      expect(open).not.toHaveBeenCalled();
    });

    it('is ignored while the warning is open', async () => {
      await pass(9 * MINUTE);
      document.dispatchEvent(new Event('click'));
      document.dispatchEvent(new Event('keydown'));

      await pass(MINUTE);
      expect(store.isLoggedIn()).toBe(false);
    });
  });

  describe('token expiry', () => {
    it('TC-12 logs out with reason=expired, without calling the server', async () => {
      logIn(new Date(NOW.getTime() + 5 * MINUTE).toISOString());
      await pass(5 * MINUTE);

      expect(logout).not.toHaveBeenCalled();
      expect(store.isLoggedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
    });

    it('activity does not extend the token', async () => {
      logIn(new Date(NOW.getTime() + 5 * MINUTE).toISOString());
      await pass(4 * MINUTE);
      document.dispatchEvent(new Event('click'));

      await pass(MINUTE);
      expect(store.isLoggedIn()).toBe(false);
    });

    it('counts down to the token end when it comes before the inactivity logout', async () => {
      logIn(new Date(NOW.getTime() + 9 * MINUTE + 20 * 1000).toISOString());
      await pass(9 * MINUTE);

      expect(secondsLeft()).toBe(20);
    });
  });

  describe('logging out in other ways', () => {
    it('stops after the logout button or a 401: no warning, no second redirect', async () => {
      logIn();
      await pass(MINUTE);
      store.clear();
      TestBed.tick();

      await pass(11 * MINUTE);
      expect(open).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('closes an open warning', async () => {
      logIn();
      await pass(9 * MINUTE);
      store.clear();
      TestBed.tick();

      expect(close).toHaveBeenCalled();
    });

    it('starts again from zero after the next login', async () => {
      logIn();
      await pass(8 * MINUTE);
      store.clear();
      TestBed.tick();

      logIn();
      await pass(9 * MINUTE - 1000);
      expect(open).not.toHaveBeenCalled();

      await pass(1000);
      expect(open).toHaveBeenCalledTimes(1);
    });
  });
});
