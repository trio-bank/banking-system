import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MAT_ICON_DEFAULT_OPTIONS, MatIcon } from '@angular/material/icon';
import { filter, map } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { LogoutButton } from '../logout-button/logout-button';
import { ThemeToggle } from '../theme-toggle/theme-toggle';

interface NavItem {
  label: string;
  icon: string;
  link: string;
  /** URL prefixes that keep the item active: account details belong to Home. */
  activeOn: string[];
}

// Transfer joins the links and tabs with its page (Sprint 3). No accounts page: they are on the dashboard.
const HOME: NavItem = {
  label: 'Home',
  icon: 'home',
  link: '/app/dashboard',
  activeOn: ['/app/dashboard', '/app/accounts'],
};
const ATM: NavItem = { label: 'ATM', icon: 'local_atm', link: '/app/atm', activeOn: ['/app/atm'] };
const PROFILE: NavItem = {
  label: 'Profile',
  icon: 'person',
  link: '/app/profile',
  activeOn: ['/app/profile'],
};

/** Toolbar shadow and white background start after this many pixels of scrolling. */
const SCROLLED_AFTER_PX = 8;

/**
 * Layout for logged-in customers (/app/**), docs/07 §13.1–13.2.
 * Wide screens: yellow toolbar with the links. Phones: the links move to bottom tabs.
 */
@Component({
  selector: 'app-customer-shell',
  imports: [RouterOutlet, RouterLink, MatIcon, LogoutButton, ThemeToggle],
  // Here and not in app.config: MatIcon in the root injector adds 13 kB to the first load.
  providers: [
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-rounded' } },
  ],
  templateUrl: './customer-shell.html',
  styleUrl: './customer-shell.scss',
  host: { '(window:scroll)': 'onScroll()' },
})
export class CustomerShell {
  private readonly router = inject(Router);
  private readonly store = inject(AuthStore);

  protected readonly topLinks = [HOME, ATM];
  // Profile is a tab because on phones the avatar is the only other way to reach it.
  protected readonly tabs = [HOME, ATM, PROFILE];

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly scrolled = signal(false);

  protected readonly initials = computed(() => {
    const customer = this.store.currentUser()?.customer;
    return customer ? `${customer.firstName[0]}${customer.lastName[0]}`.toUpperCase() : '';
  });

  protected isActive(item: NavItem): boolean {
    // Only the path counts: /app/transfer?from=… is still the Transfer page.
    const path = this.url().split(/[?#]/)[0];
    return item.activeOn.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
  }

  protected onScroll(): void {
    this.scrolled.set(window.scrollY > SCROLLED_AFTER_PX);
  }
}
