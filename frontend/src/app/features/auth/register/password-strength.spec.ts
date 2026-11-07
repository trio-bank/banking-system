import { TestBed } from '@angular/core/testing';
import { PasswordStrength } from './password-strength';

describe('PasswordStrength', () => {
  const render = async (password: string, email = '') => {
    const fixture = TestBed.createComponent(PasswordStrength);
    fixture.componentRef.setInput('password', password);
    fixture.componentRef.setInput('email', email);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  it.each([
    ['', '—'],
    ['abc', 'weak'],
    ['Abcdefghijk1', 'medium'],
    ['Str0ng!Passw0rd', 'strong'],
  ])('rates "%s" as %s', async (password, level) => {
    const element = await render(password);
    expect(element.querySelector('.label')?.textContent).toContain(`strength: ${level}`);
  });

  it('marks the rule about the email when the password contains it', async () => {
    const element = await render('Jan.Kowalski!123', 'jan.kowalski@example.com');
    const failed = [...element.querySelectorAll('li:not(.met)')];
    expect(failed).toHaveLength(1);
    expect(failed[0].textContent).toContain('email');
  });
});
