import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AddressDto, CustomerProfileDto, ProblemDetail } from '../../core/api/models';
import { AuthApi } from '../../core/api/services/auth-api';
import { AuthStore } from '../../core/auth/auth.store';
import { errorMessage } from '../../core/errors/error-messages';

/** "Krakowska 12A/4, 45-018 Opole" */
export function formatAddress(a: AddressDto): string {
  const building = a.apartmentNumber
    ? `${a.buildingNumber}/${a.apartmentNumber}`
    : a.buildingNumber;
  return `${a.street} ${building}, ${a.postalCode} ${a.city}`;
}

/** Read-only customer data (US-05). Always asks GET /me, the stored user after login has no profile. */
@Component({
  selector: 'app-profile-page',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.scss',
})
export class ProfilePage {
  private readonly api = inject(AuthApi);
  private readonly store = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly profile = signal<CustomerProfileDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formatAddress = formatAddress;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .me()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.store.setUser(user); // keep the store fresh for other pages
          this.profile.set(user.customer);
          this.loading.set(false);
        },
        error: (problem: ProblemDetail) => {
          this.error.set(errorMessage(problem.code));
          this.loading.set(false);
        },
      });
  }
}
