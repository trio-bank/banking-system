import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

// Placeholder — the registration stepper is implemented in US-01.
@Component({
  selector: 'app-register-page',
  imports: [RouterLink, MatCardModule, MatButtonModule],
  templateUrl: './register-page.html',
  styleUrl: '../auth-page.scss',
})
export class RegisterPage {}
