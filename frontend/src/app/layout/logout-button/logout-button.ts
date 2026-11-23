import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AuthStore } from '../../core/auth/auth.store';

/**
 * "Log out" in the toolbars of both shells: an outlined pill with an icon, in the bar's
 * text color (black on yellow, light on the dark and admin bars). Needs the Material Symbols
 * font set from the shell (MAT_ICON_DEFAULT_OPTIONS).
 */
@Component({
  selector: 'app-logout-button',
  imports: [MatButtonModule, MatIcon],
  template: `
    <button matButton="outlined" type="button" [disabled]="busy()" (click)="logOut()">
      <mat-icon aria-hidden="true">logout</mat-icon>
      Log out
    </button>
  `,
  styles: `
    :host {
      --mat-button-outlined-label-text-color: currentColor;
      --mat-button-outlined-state-layer-color: currentColor;
      --mat-button-outlined-outline-color: color-mix(in srgb, currentColor 35%, transparent);
      --mat-button-outlined-disabled-label-text-color: color-mix(
        in srgb,
        currentColor 50%,
        transparent
      );
      --mat-button-outlined-disabled-outline-color: color-mix(
        in srgb,
        currentColor 20%,
        transparent
      );
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
