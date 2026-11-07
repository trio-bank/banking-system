import {
  checkPesel,
  isValidPhone,
  passwordRuleFailures,
  peselBirthDate,
} from '../../../shared/validators';
import { MOCK_CUSTOMERS, MOCK_PASSWORD, MOCK_USERS } from './mock-data';

const isoDate = (date: Date | null) =>
  date &&
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

// Test data must pass the same rules as the real form, or it only confuses.
describe('mock data', () => {
  it('has no duplicates', () => {
    const unique = (values: string[]) => new Set(values).size === values.length;
    expect(unique(MOCK_USERS.map((user) => user.id))).toBe(true);
    expect(unique(MOCK_USERS.map((user) => user.email))).toBe(true);
    expect(unique(MOCK_CUSTOMERS.map((customer) => customer.pesel))).toBe(true);
    expect(unique(MOCK_CUSTOMERS.map((customer) => customer.phoneNumber))).toBe(true);
  });

  it('gives a customer profile to customers only', () => {
    for (const user of MOCK_USERS) {
      expect(user.customer !== null).toBe(user.role === 'CUSTOMER');
    }
  });

  describe.each(MOCK_CUSTOMERS)('$email', (customer) => {
    it('has a correct PESEL, and the profile shows its birth date', () => {
      expect(checkPesel(customer.pesel)).toBeNull();
      const profile = MOCK_USERS.find((user) => user.email === customer.email)?.customer;
      expect(profile?.dateOfBirth).toBe(isoDate(peselBirthDate(customer.pesel)));
      expect(profile?.peselMasked).toBe(`*******${customer.pesel.slice(-4)}`);
    });

    it('has fields in the format the form requires', () => {
      expect(isValidPhone(customer.phoneNumber)).toBe(true);
      expect(customer.address.postalCode).toMatch(/^\d{2}-\d{3}$/);
      expect(customer.address.buildingNumber.length).toBeLessThanOrEqual(10);
      expect(customer.address.apartmentNumber?.length ?? 0).toBeLessThanOrEqual(10);
    });

    it('can use the shared password', () => {
      expect(passwordRuleFailures(MOCK_PASSWORD, customer.email)).toEqual({});
    });
  });
});
