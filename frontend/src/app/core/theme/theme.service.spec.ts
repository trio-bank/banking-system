import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;

  const create = () => TestBed.inject(ThemeService);

  beforeEach(() => {
    localStorage.clear();
    root.style.removeProperty('color-scheme');
  });

  it('follows the system when nothing is saved', () => {
    const theme = create();

    expect(theme.mode()).toBe('system');
    expect(root.style.getPropertyValue('color-scheme')).toBe('');
  });

  it('restores a saved choice on start', () => {
    localStorage.setItem('trio.theme', 'dark');

    const theme = create();

    expect(theme.mode()).toBe('dark');
    expect(root.style.getPropertyValue('color-scheme')).toBe('dark');
  });

  it('ignores an unknown saved value', () => {
    localStorage.setItem('trio.theme', 'purple');

    expect(create().mode()).toBe('system');
  });

  it('pins the chosen theme and remembers it', () => {
    const theme = create();

    theme.set('light');

    expect(theme.mode()).toBe('light');
    expect(root.style.getPropertyValue('color-scheme')).toBe('light');
    expect(localStorage.getItem('trio.theme')).toBe('light');
  });

  it('goes back to the system theme and forgets the choice', () => {
    localStorage.setItem('trio.theme', 'dark');
    const theme = create();

    theme.set('system');

    expect(root.style.getPropertyValue('color-scheme')).toBe('');
    expect(localStorage.getItem('trio.theme')).toBeNull();
  });

  it('toggles to the other theme', () => {
    const theme = create();
    theme.set('light');

    theme.toggle();
    expect(theme.mode()).toBe('dark');
    expect(theme.isDark()).toBe(true);

    theme.toggle();
    expect(theme.mode()).toBe('light');
    expect(theme.isDark()).toBe(false);
  });

  describe('with the system theme', () => {
    let listeners: ((event: { matches: boolean }) => void)[];

    beforeEach(() => {
      listeners = [];
      vi.stubGlobal(
        'matchMedia',
        vi.fn(() => ({
          matches: true,
          addEventListener: (_: string, fn: (event: { matches: boolean }) => void) =>
            listeners.push(fn),
        })),
      );
    });

    afterEach(() => vi.unstubAllGlobals());

    it('knows the system is dark and follows its changes', () => {
      const theme = create();
      expect(theme.isDark()).toBe(true);

      listeners.forEach((fn) => fn({ matches: false }));

      expect(theme.isDark()).toBe(false);
    });

    it('toggles away from a dark system to light', () => {
      const theme = create();

      theme.toggle();

      expect(theme.mode()).toBe('light');
    });
  });

  it('still switches when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const theme = create();

    theme.set('dark');

    expect(theme.mode()).toBe('dark');
    expect(root.style.getPropertyValue('color-scheme')).toBe('dark');
    vi.restoreAllMocks();
  });
});
