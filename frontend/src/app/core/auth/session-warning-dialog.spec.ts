import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { SessionWarningDialog, STAY_LOGGED_IN } from './session-warning-dialog';

describe('SessionWarningDialog', () => {
  const close = vi.fn();
  const secondsLeft = signal(60);

  const render = async () => {
    const fixture = TestBed.createComponent(SessionWarningDialog);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  };

  beforeEach(() => {
    close.mockReset();
    secondsLeft.set(60);
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { secondsLeft } },
      ],
    });
  });

  it('shows the countdown and keeps it up to date', async () => {
    const { fixture, element } = await render();
    expect(element.textContent).toContain('60 s');

    secondsLeft.set(42);
    await fixture.whenStable();
    expect(element.textContent).toContain('42 s');
  });

  it('closes with "stay" when the user presses the button', async () => {
    const { element } = await render();
    const button = element.querySelector('button');

    expect(button?.textContent).toContain('Stay logged in');
    button?.click();
    expect(close).toHaveBeenCalledWith(STAY_LOGGED_IN);
  });
});
