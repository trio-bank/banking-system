import { DOCUMENT, effect, inject, Injectable, signal } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NavigationEnd, Router } from '@angular/router';
import { filter, fromEvent, merge, Subscription } from 'rxjs';
import { AuthStore } from './auth.store';
import { SessionWarningDialog, STAY_LOGGED_IN } from './session-warning-dialog';

const MINUTE_MS = 60 * 1000;
const WARN_AFTER_MS = 9 * MINUTE_MS;
const LOGOUT_AFTER_MS = 10 * MINUTE_MS;

/**
 * Ends the session when the user is inactive or the token runs out.
 * Runs by itself while someone is logged in, so every way of logging out also stops it.
 */
@Injectable({ providedIn: 'root' })
export class SessionTimer {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly document = inject(DOCUMENT);

  private readonly secondsLeft = signal(0);
  private lastActivity = 0;
  private ticker: ReturnType<typeof setInterval> | undefined;
  private activity: Subscription | undefined;
  private warning: MatDialogRef<SessionWarningDialog, string> | undefined;

  constructor() {
    effect(() => {
      if (this.store.isLoggedIn()) {
        this.start();
      } else {
        this.stop();
      }
    });
  }

  private start(): void {
    this.stop();
    this.lastActivity = Date.now();
    // Compares clock times every second, so it also works after a laptop wakes up from sleep.
    this.ticker = setInterval(() => this.tick(), 1000);
    this.activity = merge(
      fromEvent(this.document, 'click'),
      fromEvent(this.document, 'keydown'),
      this.router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    ).subscribe(() => {
      // Once the warning is open, only "Stay logged in" counts: a random key press shouldn't.
      if (!this.warning) {
        this.lastActivity = Date.now();
      }
    });
  }

  private stop(): void {
    clearInterval(this.ticker);
    this.activity?.unsubscribe();
    this.warning?.close();
    this.warning = undefined;
  }

  private tick(): void {
    const now = Date.now();
    const expiresAt = Date.parse(this.store.expiresAt() ?? '');
    const logoutAt = this.lastActivity + LOGOUT_AFTER_MS;

    if (now >= expiresAt) {
      void this.end('expired');
    } else if (now >= logoutAt) {
      void this.end('inactive');
    } else if (now >= this.lastActivity + WARN_AFTER_MS) {
      this.secondsLeft.set(Math.ceil((Math.min(logoutAt, expiresAt) - now) / 1000));
      this.showWarning();
    }
  }

  private showWarning(): void {
    if (this.warning) {
      return;
    }
    this.warning = this.dialog.open<SessionWarningDialog, unknown, string>(SessionWarningDialog, {
      data: { secondsLeft: this.secondsLeft.asReadonly() },
      disableClose: true, // Esc or a click outside must not count as "I'm still here"
      role: 'alertdialog',
    });
    this.warning.afterClosed().subscribe((result) => {
      if (result === STAY_LOGGED_IN) {
        this.lastActivity = Date.now();
        this.warning = undefined;
      }
    });
  }

  private async end(reason: 'expired' | 'inactive'): Promise<void> {
    this.stop();
    if (reason === 'inactive') {
      await this.store.logout();
    } else {
      this.store.clear(); // the server would reject an expired token anyway
    }
    await this.router.navigate(['/login'], { queryParams: { reason } });
  }
}
