import { AccountDto, AddressDto, CurrentUserDto, UserStatus } from '../models';

/** One password for all test users. */
export const MOCK_PASSWORD = 'Str0ng!Passw0rd';

/** A customer who already exists in the bank. Free data for registering is in MOCK_DATA.md. */
export interface MockCustomer {
  email: string;
  firstName: string;
  lastName: string;
  pesel: string;
  phoneNumber: string;
  address: AddressDto;
  status?: UserStatus; // ACTIVE if not set
  lastLoginAt?: string | null; // null = has never logged in before
}

const address = (
  street: string,
  buildingNumber: string,
  apartmentNumber: string | null,
  postalCode: string,
  city: string,
): AddressDto => ({ street, buildingNumber, apartmentNumber, postalCode, city, countryCode: 'PL' });

// All names and numbers are made up. The PESEL check digits are correct.
export const MOCK_CUSTOMERS: MockCustomer[] = [
  {
    email: 'anna.nowak@example.com',
    firstName: 'Anna',
    lastName: 'Nowak',
    pesel: '90051412343',
    phoneNumber: '+48501234567',
    address: address('Krakowska', '12A', '4', '45-018', 'Opole'),
  },
  {
    email: 'locked@example.com',
    firstName: 'Piotr',
    lastName: 'Lis',
    pesel: '82031710413',
    phoneNumber: '+48502000001',
    address: address('Ozimska', '8', '15', '45-057', 'Opole'),
    status: 'LOCKED',
  },
  {
    email: 'blocked@example.com',
    firstName: 'Ewa',
    lastName: 'Wrona',
    pesel: '75090821123',
    phoneNumber: '+48502000002',
    address: address('Długa', '21', null, '31-147', 'Kraków'),
    status: 'BLOCKED',
  },
  {
    email: 'michal.kaczmarek@example.com',
    firstName: 'Michał',
    lastName: 'Kaczmarek',
    pesel: '96100517797',
    phoneNumber: '+48502000003',
    address: address('Leśna', '9', '2', '15-001', 'Białystok'),
    lastLoginAt: null, // first login, no "Last login" to show
  },
];

const uuid = (group: string, n: number) =>
  `00000000-0000-4000-${group}-${String(n).padStart(12, '0')}`;

/** 90051412343 → 1990-05-14. Only for our list, where the numbers are known to be correct. */
function birthDateOf(pesel: string): string {
  const month = Number(pesel.slice(2, 4));
  const century = month > 20 ? 2000 : 1900;
  const mm = String(month % 20).padStart(2, '0');
  return `${century + Number(pesel.slice(0, 2))}-${mm}-${pesel.slice(4, 6)}`;
}

function toUser(customer: MockCustomer, index: number): CurrentUserDto {
  const n = index + 1;
  return {
    id: uuid('a000', n),
    email: customer.email,
    role: 'CUSTOMER',
    status: customer.status ?? 'ACTIVE',
    lastLoginAt: customer.lastLoginAt === undefined ? '2026-10-23T18:10:00Z' : customer.lastLoginAt,
    customer: {
      id: uuid('b000', n),
      // New registrations get a number outside this range.
      customerNumber: String(10000000 + n),
      firstName: customer.firstName,
      lastName: customer.lastName,
      peselMasked: `*******${customer.pesel.slice(-4)}`,
      dateOfBirth: birthDateOf(customer.pesel),
      email: customer.email,
      phoneNumber: customer.phoneNumber,
      address: customer.address,
      createdAt: '2026-10-24T13:02:11Z',
    },
  };
}

const ADMIN: CurrentUserDto = {
  id: uuid('c000', 1),
  email: 'admin@triobank.example',
  role: 'ADMIN',
  status: 'ACTIVE',
  lastLoginAt: null,
  customer: null,
};

const [ANNA, ...OTHER_CUSTOMERS] = MOCK_CUSTOMERS.map(toUser);

/** Log in with any of these emails + MOCK_PASSWORD. The first two are the usual customer and admin. */
export const MOCK_USERS: CurrentUserDto[] = [ANNA, ADMIN, ...OTHER_CUSTOMERS];

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
