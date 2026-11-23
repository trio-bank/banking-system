import { Component, computed, inject } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { ThemeService } from '../../core/theme/theme.service';

/** Moon / sun button in the toolbar. Shows the theme you switch to, not the current one. */
@Component({
  selector: 'app-theme-toggle',
  imports: [MatIconButton, MatIcon],
  template: `
    <button
      matIconButton
      type="button"
      [attr.aria-label]="label()"
      [attr.title]="label()"
      (click)="theme.toggle()"
    >
      <mat-icon>{{ theme.isDark() ? 'light_mode' : 'dark_mode' }}</mat-icon>
    </button>
  `,
  styles: `
    /* Takes the toolbar's text color: black on yellow, ink on the scrolled bar. */
    :host {
      --mat-icon-button-icon-color: currentColor;
      --mat-icon-button-state-layer-color: currentColor;
    }
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);

  protected readonly label = computed(() =>
    this.theme.isDark() ? 'Switch to light theme' : 'Switch to dark theme',
  );
}
