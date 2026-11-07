import { Component, inject, Signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export const STAY_LOGGED_IN = 'stay';

export interface SessionWarningData {
  secondsLeft: Signal<number>;
}

/** The warning after 9 minutes of inactivity. */
@Component({
  selector: 'app-session-warning-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Are you still there?</h2>
    <mat-dialog-content>
      You will be logged out in <strong>{{ data.secondsLeft() }} s</strong> because of inactivity.
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton="filled" type="button" [mat-dialog-close]="stay">Stay logged in</button>
    </mat-dialog-actions>
  `,
})
export class SessionWarningDialog {
  protected readonly data = inject<SessionWarningData>(MAT_DIALOG_DATA);
  protected readonly stay = STAY_LOGGED_IN;
}
