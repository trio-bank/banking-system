import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize, switchMap, tap } from 'rxjs';
import { ProblemDetail } from '../../../core/api/models';
import { AuthApi } from '../../../core/api/services/auth-api';
import { AuthStore, homeUrl } from '../../../core/auth/auth.store';
import { errorMessage, fieldErrorMessage } from '../../../core/errors/error-messages';
import { AuthLayout } from '../auth-layout/auth-layout';

// Why the user ended up here: /login?reason=expired
const REASON_MESSAGES: Record<string, string> = {
  expired: errorMessage('UNAUTHENTICATED'),
  inactive: 'You were logged out because of inactivity. Please log in again.',
};

@Component({
  selector: 'app-login-page',
  imports: [
    AuthLayout,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './login-page.html',
  styleUrls: ['../auth-page.scss', './login-page.scss'],
})
export class LoginPage {
  private readonly authApi = inject(AuthApi);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    // Only "required": the strength rules would tell an attacker what passwords can't be.
    password: ['', Validators.required],
  });

  protected readonly notice = signal<string | null>(
    REASON_MESSAGES[inject(ActivatedRoute).snapshot.queryParamMap.get('reason') ?? ''] ?? null,
  );
  protected readonly banner = signal<string | null>(null);
  protected readonly submitting = signal(false);

  // The lock is on one user, so it only applies while the same email stays in the form.
  protected readonly locked = signal(false);
  private readonly lockedUntil = signal<number | null>(null);
  private readonly now = signal(Date.now());
  private countdown: ReturnType<typeof setInterval> | undefined;

  /** "14:32" until the user can try again. */
  protected readonly timeLeft = computed(() => {
    const until = this.lockedUntil();
    if (until === null) {
      return null;
    }
    const seconds = Math.max(0, Math.ceil((until - this.now()) / 1000));
    return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
  });

  constructor() {
    this.form.controls.email.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.locked()) {
        this.clearLock();
        this.banner.set(null);
      }
    });
    this.destroyRef.onDestroy(() => this.stopCountdown());
  }

  protected emailError(): string {
    return this.form.controls.email.hasError('required')
      ? fieldErrorMessage('REQUIRED')
      : 'Enter a valid email address.';
  }

  protected passwordError(): string {
    return fieldErrorMessage('REQUIRED');
  }

  protected submit(): void {
    if (this.submitting() || this.locked()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.notice.set(null);
    this.banner.set(null);
    this.submitting.set(true);

    const { email, password } = this.form.getRawValue();
    this.authApi
      .login({ email: email.trim(), password })
      .pipe(
        // The token must be saved first: GET /me needs it.
        tap((response) => this.store.setSession(response)),
        switchMap(() => this.authApi.me()),
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (user) => {
          this.store.setUser(user);
          void this.router.navigateByUrl(homeUrl(user.role));
        },
        error: (problem: ProblemDetail) => this.showProblem(problem),
      });
  }

  private showProblem(problem: ProblemDetail): void {
    this.store.clear(); // login may have worked, but without /me the session is useless
    if (problem.status >= 500 || problem.status === 0) {
      const errorId = problem.traceId ? ` Error ID: ${problem.traceId}` : '';
      this.snackBar.open(errorMessage(problem.code) + errorId, 'Close');
      return;
    }
    this.banner.set(errorMessage(problem.code));
    if (problem.code === 'USER_LOCKED') {
      this.lock(problem.lockedUntil);
    }
  }

  private lock(lockedUntil: string | undefined): void {
    const until = Date.parse(lockedUntil ?? '');
    if (Number.isNaN(until)) {
      return; // no time to count down to, the message alone is enough
    }
    this.locked.set(true);
    this.lockedUntil.set(until);
    this.now.set(Date.now());
    this.stopCountdown();
    this.countdown = setInterval(() => {
      this.now.set(Date.now());
      if (this.now() >= until) {
        this.clearLock();
        this.banner.set(null);
      }
    }, 1000);
  }

  private clearLock(): void {
    this.stopCountdown();
    this.locked.set(false);
    this.lockedUntil.set(null);
  }

  private stopCountdown(): void {
    clearInterval(this.countdown);
  }
}
