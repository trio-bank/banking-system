import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { LogoutButton } from '../logout-button/logout-button';

// Layout for logged-in customers (/app/**). Side nav and user menu come with the dashboard stories.
@Component({
  selector: 'app-customer-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatToolbarModule,
    LogoutButton,
  ],
  templateUrl: './customer-shell.html',
  styleUrl: './customer-shell.scss',
})
export class CustomerShell {}
