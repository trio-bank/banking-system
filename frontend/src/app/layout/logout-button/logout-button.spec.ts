import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { LogoutButton } from './logout-button';

describe('LogoutButton', () => {
  const logout = vi.fn();

  const render = async () => {
    const fixture = TestBed.createComponent(LogoutButton);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button');
    if (!button) {
      throw new Error('no button');
    }
    return { fixture, button };
  };

  beforeEach(() => {
    logout.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: { logout } }],
    });
  });

  it('goes to /login, without a reason, after the logout', async () => {
    logout.mockResolvedValue(undefined);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, button } = await render();

    button.click();
    await fixture.whenStable();

    expect(logout).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('waits for the logout before leaving and cannot be pressed twice', async () => {
    let finish: () => void = () => undefined;
    logout.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, button } = await render();

    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(navigate).not.toHaveBeenCalled();

    finish();
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledTimes(1);
  });
});
