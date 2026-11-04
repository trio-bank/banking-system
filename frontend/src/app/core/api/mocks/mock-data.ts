import { AccountDto, CurrentUserDto, CustomerProfileDto } from '../models';

/** One password for all test users. */
export const MOCK_PASSWORD = 'Str0ng!Passw0rd';

export const ANNA_PROFILE: CustomerProfileDto = {
  id: '3c9a1f70-2b4d-4e8a-9f61-5d2e7c8b1a20',
  customerNumber: '10000001',
  firstName: 'Anna',
  lastName: 'Nowak',
  peselMasked: '*******2343',
  dateOfBirth: '1990-05-14',
  email: 'anna.nowak@example.com',
  phoneNumber: '+48501234567',
  address: {
    street: 'Krakowska',
    buildingNumber: '12A',
    apartmentNumber: '4',
    postalCode: '45-018',
    city: 'Opole',
    countryCode: 'PL',
  },
  createdAt: '2026-10-24T13:02:11Z',
};

/**
 * Log in with any of these emails + MOCK_PASSWORD:
 * anna = customer, admin = admin, locked/blocked = login errors.
 */
export const MOCK_USERS: CurrentUserDto[] = [
  {
    id: '0b6f2a3e-8f44-4f0e-9a51-2c1e5d7b9a10',
    email: 'anna.nowak@example.com',
    role: 'CUSTOMER',
    status: 'ACTIVE',
    lastLoginAt: '2026-10-23T18:10:00Z',
    customer: ANNA_PROFILE,
  },
  {
    id: 'a1d4e6f8-0c2b-4d9e-8f13-6b7a5c4d3e21',
    email: 'admin@triobank.example',
    role: 'ADMIN',
    status: 'ACTIVE',
    lastLoginAt: null,
    customer: null,
  },
  {
    id: 'c5e7a9b1-3d4f-4a6c-8e20-1f2a3b4c5d62',
    email: 'locked@example.com',
    role: 'CUSTOMER',
    status: 'LOCKED',
    lastLoginAt: null,
    customer: null,
  },
  {
    id: 'e8f0b2c4-6a7d-4c9e-b153-2a3b4c5d6e73',
    email: 'blocked@example.com',
    role: 'CUSTOMER',
    status: 'BLOCKED',
    lastLoginAt: null,
    customer: null,
  },
];

/** The personal account every new customer gets (example from docs/05 A1). */
export const NEW_PERSONAL_ACCOUNT: AccountDto = {
  id: '7d1e4c22-5b3a-4a8e-b0f1-93c2d4e5f601',
  iban: 'PL45999000121000000000000001',
  name: 'Trio Personal Account',
  type: 'PERSONAL',
  productCode: 'PERSONAL_STANDARD',
  productName: 'Trio Personal Account',
  currency: 'PLN',
  balance: '0.00',
  status: 'ACTIVE',
  interestRate: '0.0000',
  accruedInterest: null,
  openedAt: '2026-10-24T13:02:11Z',
};
