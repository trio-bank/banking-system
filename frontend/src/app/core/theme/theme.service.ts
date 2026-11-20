import { DOCUMENT, Injectable, inject, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'trio.theme';

/**
 * Light or dark theme. By default the app follows the system; a manual choice
 * is kept in localStorage so it survives logout and new tabs.
 * The colors themselves switch in styles.scss through light-dark().
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly current = signal<ThemeMode>(this.read());
  readonly mode = this.current.asReadonly();

  constructor() {
    this.apply(this.current());
  }

  set(mode: ThemeMode): void {
    this.current.set(mode);
    this.apply(mode);
    this.write(mode);
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
