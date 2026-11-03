import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';

// Layout for logged-in customers (/app/**). Side nav and user menu come with the dashboard stories.
@Component({
  selector: 'app-customer-shell',
  imports: [RouterOutlet, RouterLink, MatToolbarModule],
  templateUrl: './customer-shell.html',
  styleUrl: './customer-shell.scss',
})
export class CustomerShell {}
