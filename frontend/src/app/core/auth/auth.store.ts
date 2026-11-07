import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { AuthApi } from '../api/services/auth-api';
import { CurrentUserDto, IsoDateTime, LoginResponse, Role } from '../api/models';

const STORAGE_KEY = 'trio.session';
const LOGOUT_TIMEOUT_MS = 5000;

interface StoredSession {
  accessToken: string;
  expiresAt: IsoDateTime;
}

/** Where each role lands after login, or when it opens a page it can't use. */
export function homeUrl(role: Role | null): string {
  if (role === 'CUSTOMER') return '/app/dashboard';
  if (role === 'ADMIN') return '/admin/customers';
  return '/login';
}

/**
 * Who is logged in. The token lives in sessionStorage (gone when the tab closes),
 * the user is loaded again via GET /me after a page reload.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly session = signal<StoredSession | null>(readSession());

  readonly currentUser = signal<CurrentUserDto | null>(null);
  readonly token = computed(() => this.session()?.accessToken ?? null);
  readonly expiresAt = computed(() => this.session()?.expiresAt ?? null);
  readonly isLoggedIn = computed(() => this.token() !== null && this.currentUser() !== null);
  readonly role = computed(() => this.currentUser()?.role ?? null);

  /** Call after a successful login. */
  setSession(response: LoginResponse): void {
    const session = { accessToken: response.accessToken, expiresAt: response.expiresAt };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
    this.currentUser.set(response.user);
  }

  /** E.g. after GET /me returns the full profile. */
  setUser(user: CurrentUserDto): void {
    this.currentUser.set(user);
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    this.currentUser.set(null);
  }

  /** Tells the server, then forgets the session. The user gets out even if the server doesn't answer. */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout().pipe(timeout(LOGOUT_TIMEOUT_MS)));
    } catch {
      // Nothing to do: the token is only removed from the browser anyway.
    } finally {
      this.clear();
    }
  }

  /** On app start: if a saved token is still valid, load the user again. */
  async restore(): Promise<void> {
    const expiresAt = this.expiresAt();
    if (!expiresAt) return;
    if (Date.parse(expiresAt) <= Date.now()) {
      this.clear();
      return;
    }
    try {
      this.currentUser.set(await firstValueFrom(this.api.me()));
    } catch {
      this.clear();
    }
  }
}

function readSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null; // broken value or storage not available
  }
}
