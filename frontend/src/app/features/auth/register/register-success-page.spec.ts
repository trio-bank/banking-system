import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { RegistrationResponse } from '../../../core/api/models';
import { NEW_PERSONAL_ACCOUNT } from '../../../core/api/mocks/mock-data';
import { RegisterSuccessPage } from './register-success-page';

const registration: RegistrationResponse = {
  userId: 'user-id',
  customerNumber: '10000002',
  email: 'new.user@example.com',
  account: NEW_PERSONAL_ACCOUNT,
};

@Component({ template: '' })
class EmptyPage {}

describe('RegisterSuccessPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'register/success', component: RegisterSuccessPage },
          { path: 'register', component: EmptyPage },
          { path: 'login', component: EmptyPage },
        ]),
      ],
    });
  });

  it('TC-01 shows the customer number and the formatted IBAN', async () => {
    const harness = await RouterTestingHarness.create();
    await TestBed.inject(Router).navigateByUrl('/register/success', { state: { registration } });
    await harness.fixture.whenStable();

    const text = harness.routeNativeElement?.textContent;
    expect(text).toContain('10000002');
    expect(text).toContain('PL45 9990 0012 1000 0000 0000 0001');
  });

  it('has a link to the login page', async () => {
    const harness = await RouterTestingHarness.create();
    await TestBed.inject(Router).navigateByUrl('/register/success', { state: { registration } });
    await harness.fixture.whenStable();

    const link = harness.routeNativeElement?.querySelector('mat-card a');
    expect(link?.getAttribute('href')).toBe('/login');
    expect(link?.textContent).toContain('Go to login');
  });

  it('goes to /register when opened without a result', async () => {
    const harness = await RouterTestingHarness.create();
    await TestBed.inject(Router).navigateByUrl('/register/success');
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/register');
  });
});
