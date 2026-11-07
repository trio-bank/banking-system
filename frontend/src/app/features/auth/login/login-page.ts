import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthLayout } from '../auth-layout/auth-layout';

// Placeholder — the login form is implemented in US-02.
@Component({
  selector: 'app-login-page',
  imports: [RouterLink, MatCardModule, MatButtonModule, AuthLayout],
  templateUrl: './login-page.html',
  styleUrl: '../auth-page.scss',
})
export class LoginPage {}
