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
    /* The toolbar is black: the default hover and disabled colors are dark too. */
    :host {
      --mat-button-text-state-layer-color: var(--mat-sys-on-primary);
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
