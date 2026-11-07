import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Frame of the guest pages: black header on top, page content centered below. */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink],
  template: `
    <header class="auth-header">
      <a class="brand" routerLink="/login">
        <span class="brand-mark" aria-hidden="true"></span>
        Trio Bank
      </a>
    </header>
    <main class="auth-main">
      <ng-content />
    </main>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }

    .auth-header {
      padding: 16px 24px;
      background: var(--mat-sys-primary);
      border-bottom: 3px solid var(--mat-sys-tertiary);
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: var(--mat-sys-on-primary);
      text-decoration: none;
      font: var(--mat-sys-title-large);
    }

    .auth-main {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      padding: 40px 16px;
    }

    @media (max-width: 600px) {
      .auth-main {
        padding: 16px;
      }
    }
  `,
})
export class AuthLayout {}
