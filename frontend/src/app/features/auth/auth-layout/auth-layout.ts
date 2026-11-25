import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Frame of the guest pages: yellow header with the logo and a big title, the page's form
 * card next to it. Phone: header on top, the card overlaps it. ≥ 768px: split screen.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink],
  template: `
    <main class="guest">
      <section class="hero">
        <a class="brand" routerLink="/login">
          <span class="brand-mark" aria-hidden="true"></span>
          Trio Bank
        </a>
        <div class="welcome">
          <h1>{{ title() }}</h1>
          <p>{{ subtitle() }}</p>
        </div>
      </section>
      <section class="form-side">
        <div class="form-card" [class.wide]="wide()">
          <ng-content />
        </div>
      </section>
    </main>
  `,
  styleUrl: './auth-layout.scss',
})
export class AuthLayout {
  readonly title = input.required<string>();
  readonly subtitle = input.required<string>();
  /** Wider card for long forms (register). */
  readonly wide = input(false);
}
