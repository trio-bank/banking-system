import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthStore } from '../../core/auth/auth.store';

/** "Log out" in the toolbars of both shells. */
@Component({
  selector: 'app-logout-button',
  imports: [MatButtonModule],
  template: `
    <button matButton type="button" [disabled]="busy()" (click)="logOut()">Log out</button>
  `,
  styles: `
    /* Takes the toolbar's text color: white on the admin bar, black on the yellow one. */
    :host {
      --mat-button-text-label-text-color: currentColor;
      --mat-button-text-state-layer-color: currentColor;
      --mat-button-text-disabled-label-text-color: var(--mat-sys-outline);
    }
  `,
})
export class LogoutButton {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);

  protected async logOut(): Promise<void> {
    this.busy.set(true);
    await this.store.logout();
    await this.router.navigateByUrl('/login');
  }
}
