import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CurrentUserDto } from '../../core/api/models';
import { MOCK_USERS } from '../../core/api/mocks/mock-data';
import { AuthStore } from '../../core/auth/auth.store';
import { CustomerShell } from './customer-shell';

@Component({ template: '' })
class EmptyPage {}

describe('CustomerShell', () => {
  const currentUser = signal<CurrentUserDto | null>(MOCK_USERS[0]);
  const logout = vi.fn();

  const render = async (url: string) => {
    const fixture = TestBed.createComponent(CustomerShell);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  // [label, aria-current] of every link in the given nav
  const links = (el: HTMLElement, nav: '.links' | '.tabs') =>
    [...el.querySelectorAll<HTMLAnchorElement>(`${nav} a`)].map((a) => [
      a.textContent?.replace(/\s+/g, ' ').trim(),
      a.getAttribute('aria-current'),
    ]);

  // The menu panel is rendered in the CDK overlay at the end of <body>, not in the shell.
  const openMenu = (el: HTMLElement) => {
    el.querySelector<HTMLButtonElement>('button.phone-only')!.click();
    TestBed.tick();
    return document.querySelector<HTMLElement>('.account-menu')!;
  };

  beforeEach(() => {
    logout.mockReset().mockResolvedValue(undefined);
    localStorage.clear();
    document.documentElement.style.removeProperty('color-scheme');
    currentUser.set(MOCK_USERS[0]);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'app/dashboard', component: EmptyPage },
          { path: 'app/accounts/:id', component: EmptyPage },
          { path: 'app/atm', component: EmptyPage },
          { path: 'app/atm-history', component: EmptyPage },
          { path: 'app/profile', component: EmptyPage },
        ]),
        { provide: AuthStore, useValue: { currentUser, logout } },
      ],
    });
  });

  it('marks Home as the current page on the dashboard', async () => {
    const el = await render('/app/dashboard');

    expect(links(el, '.links')).toEqual([
      ['home Home', 'page'],
      ['local_atm ATM', null],
    ]);
  });

  it('keeps Home active on account details', async () => {
    const el = await render('/app/accounts/42');

    expect(links(el, '.links')[0][1]).toBe('page');
  });

  it('ignores the query and fragment of the URL', async () => {
    const el = await render('/app/atm?tab=withdraw#amount');

    expect(links(el, '.links')[1][1]).toBe('page');
  });

  it('does not take a longer path with the same start as its own', async () => {
    const el = await render('/app/atm-history');

    expect(links(el, '.links').every(([, current]) => current === null)).toBe(true);
  });

  it('has Profile in the bottom tabs and marks it there', async () => {
    const el = await render('/app/profile');

    expect(links(el, '.tabs')).toEqual([
      ['home Home', null],
      ['local_atm ATM', null],
      ['person Profile', 'page'],
    ]);
    expect(links(el, '.links').every(([, current]) => current === null)).toBe(true);
  });

  it('fills the icon of the active item only', async () => {
    const el = await render('/app/atm');

    const filled = [...el.querySelectorAll('.links mat-icon.filled')].map((i) => i.textContent);
    expect(filled).toEqual(['local_atm']);
  });

  it('shows the initials of the customer in the avatar', async () => {
    const customer = MOCK_USERS[0].customer;
    const el = await render('/app/dashboard');

    expect(el.querySelector('.avatar')?.textContent?.trim()).toBe(
      `${customer?.firstName[0]}${customer?.lastName[0]}`.toUpperCase(),
    );
  });

  it('turns the bar into the page surface after scrolling', async () => {
    const el = await render('/app/dashboard');
    const bar = el.querySelector('.topbar');

    window.scrollY = 120;
    window.dispatchEvent(new Event('scroll'));
    TestBed.tick();
    expect(bar?.classList).toContain('scrolled');

    window.scrollY = 0;
    window.dispatchEvent(new Event('scroll'));
    TestBed.tick();
    expect(bar?.classList).not.toContain('scrolled');
  });

  describe('account menu (phones)', () => {
    it('shows who is logged in and links to the profile', async () => {
      const customer = MOCK_USERS[0].customer!;
      const menu = openMenu(await render('/app/dashboard'));

      expect(menu.querySelector('.menu-name')?.textContent).toContain(
        `${customer.firstName} ${customer.lastName}`,
      );
      expect(menu.querySelector('.menu-email')?.textContent).toContain(MOCK_USERS[0].email);
      expect(menu.querySelector('a[mat-menu-item]')?.getAttribute('href')).toBe('/app/profile');
    });

    it('switches the theme', async () => {
      const menu = openMenu(await render('/app/dashboard'));
      const themeItem = menu.querySelectorAll<HTMLButtonElement>('button[mat-menu-item]')[0];
      const before = themeItem.textContent;

      themeItem.click();
      TestBed.tick();

      expect(document.documentElement.style.getPropertyValue('color-scheme')).not.toBe('');
      expect(themeItem.textContent).not.toBe(before);
    });

    it('logs out and goes to /login', async () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      const menu = openMenu(await render('/app/dashboard'));

      menu.querySelectorAll<HTMLButtonElement>('button[mat-menu-item]')[1].click();
      await Promise.resolve();
      await Promise.resolve();

      expect(logout).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledWith('/login');
    });
  });
});
