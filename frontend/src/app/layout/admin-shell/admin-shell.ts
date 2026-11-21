import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { LogoutButton } from '../logout-button/logout-button';

// Layout for bank staff (/admin/**). Admin navigation comes with the admin stories.
@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, LogoutButton],
  // The logout button has an icon; the font set lives here, not in app.config (+13 kB).
  providers: [
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-rounded' } },
  ],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.scss',
})
export class AdminShell {}
