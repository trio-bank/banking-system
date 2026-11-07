import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { LogoutButton } from '../logout-button/logout-button';

// Layout for bank staff (/admin/**). Admin navigation comes with the admin stories.
@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, LogoutButton],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.scss',
})
export class AdminShell {}
