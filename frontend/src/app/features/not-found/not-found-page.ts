import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, MatButtonModule],
  template: `
    <main class="not-found">
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist.</p>
      <a mat-flat-button routerLink="/">Go to the start page</a>
    </main>
  `,
  styles: `
    .not-found {
      padding: 48px 16px;
      text-align: center;
    }
  `,
})
export class NotFoundPage {}
