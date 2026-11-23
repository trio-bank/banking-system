import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'trio.theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

/**
 * Light or dark theme. By default the app follows the system; a manual choice
 * is kept in localStorage so it survives logout and new tabs.
 * The colors themselves switch in styles.scss through light-dark().
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly current = signal<ThemeMode>(this.read());
  private readonly systemDark = signal(false);

  readonly mode = this.current.asReadonly();
  /** What the user actually sees, also when the mode is "system". */
  readonly isDark = computed(() =>
    this.current() === 'system' ? this.systemDark() : this.current() === 'dark',
  );

  constructor() {
    this.apply(this.current());
    this.watchSystem();
  }

  set(mode: ThemeMode): void {
    this.current.set(mode);
    this.apply(mode);
    this.write(mode);
  }

  /** The toolbar button: switches to the other theme and remembers it. */
  toggle(): void {
    this.set(this.isDark() ? 'light' : 'dark');
  }

  // "system" removes the inline value, so styles.scss ("light dark") decides again.
  private apply(mode: ThemeMode): void {
    const style = this.document.documentElement.style;
    if (mode === 'system') {
      style.removeProperty('color-scheme');
    } else {
      style.setProperty('color-scheme', mode);
    }
  }

  // The service lives as long as the app, so the listener is never removed.
  private watchSystem(): void {
    const query = this.document.defaultView?.matchMedia?.(DARK_QUERY);
    if (!query) {
      return; // jsdom and very old browsers
    }
    this.systemDark.set(query.matches);
    query.addEventListener('change', (event) => this.systemDark.set(event.matches));
  }

  // Storage can be blocked (private mode, strict settings): then we just don't remember.
  private read(): ThemeMode {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      return saved === 'light' || saved === 'dark' ? saved : 'system';
    } catch {
      return 'system';
    }
  }

  private write(mode: ThemeMode): void {
    try {
      if (mode === 'system') {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, mode);
      }
    } catch {
      // not remembered, the choice still works until reload
    }
  }
}
