import { Location } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RegistrationResponse } from '../../../core/api/models';
import { IbanPipe } from '../../../shared/pipes';
import { AuthLayout } from '../auth-layout/auth-layout';

/** Gets the result from the navigation state. Opening this URL directly leads to /register. */
@Component({
  selector: 'app-register-success-page',
  imports: [AuthLayout, IbanPipe, MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './register-success-page.html',
  styleUrls: ['../auth-page.scss', './register-success-page.scss'],
})
export class RegisterSuccessPage {
  protected readonly registration = (
    inject(Location).getState() as { registration?: RegistrationResponse } | null
  )?.registration;

  constructor() {
    if (!this.registration) {
      void inject(Router).navigate(['/register'], { replaceUrl: true });
    }
  }
}
