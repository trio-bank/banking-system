import { AccountDto, AddressDto, CurrentUserDto, UserStatus } from '../models';

/** One password for all test users. */
export const MOCK_PASSWORD = 'Str0ng!Passw0rd';

/** Everything we need to know about a test customer. The list is also in MOCK_DATA.md. */
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
  // Login errors
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
  // Names: Polish letters, double names, apostrophes, very long values
  {
    email: 'tomasz.wisniewski@example.com',
    firstName: 'Tomasz',
    lastName: 'Wiśniewski',
    pesel: '88062331836',
    phoneNumber: '+48502000003',
    address: address('Świętokrzyska', '30', '7', '00-116', 'Warszawa'),
  },
  {
    email: 'katarzyna.nowak-lewandowska@example.com',
    firstName: 'Katarzyna',
    lastName: 'Nowak-Lewandowska',
    pesel: '93120142541',
    phoneNumber: '+48502000004',
    address: address('Piotrkowska', '104', '12', '90-006', 'Łódź'),
  },
  {
    email: 'sean.oconnor@example.com',
    firstName: 'Seán',
    lastName: "O'Connor",
    pesel: '79043053258',
    phoneNumber: '+48502000005',
    address: address('Grunwaldzka', '82', '3', '80-244', 'Gdańsk'),
  },
  {
    email: 'aleksandra.wierzbicka-szczepanska@example.com',
    firstName: 'Aleksandra Konstancja',
    lastName: 'Wierzbicka-Szczepańska',
    pesel: '91071996000',
    phoneNumber: '+48502000006',
    address: address('Aleja Marszałka Józefa Piłsudskiego', '123B', '1045', '50-001', 'Wrocław'),
  },
  // Age and birth dates
  {
    email: 'oliwia.mazur@example.com',
    firstName: 'Oliwia',
    lastName: 'Mazur',
    pesel: '08310163967', // 18 years old since 01.11.2026
    phoneNumber: '+48502000007',
    address: address('Kwiatowa', '5', null, '40-001', 'Katowice'),
  },
  {
    email: 'stanislaw.dabrowski@example.com',
    firstName: 'Stanisław',
    lastName: 'Dąbrowski',
    pesel: '38021174679', // the oldest customer
    phoneNumber: '+48502000008',
    address: address('Lipowa', '2', '1', '20-020', 'Lublin'),
  },
  {
    email: 'zofia.kaminska@example.com',
    firstName: 'Zofia',
    lastName: 'Kamińska',
    pesel: '00210185386', // 01.01.2000, the first day of the 2000s
    phoneNumber: '+48502000009',
    address: address('Słoneczna', '19', '6', '61-001', 'Poznań'),
  },
  {
    email: 'adam.pawlak@example.com',
    firstName: 'Adam',
    lastName: 'Pawlak',
    pesel: '00222939113', // 29.02.2000, a leap day
    phoneNumber: '+48502000010',
    address: address('Polna', '44', null, '70-001', 'Szczecin'),
  },
  // Other cases
  {
    email: 'michal.kaczmarek@example.com',
    firstName: 'Michał',
    lastName: 'Kaczmarek',
    pesel: '96100517797',
    phoneNumber: '+48502000011',
    address: address('Leśna', '9', '2', '15-001', 'Białystok'),
    lastLoginAt: null, // first login, no "Last login" to show
  },
  {
    email: 'agnieszka.krol@example.com',
    firstName: 'Agnieszka',
    lastName: 'Król',
    pesel: '84052728426',
    phoneNumber: '+48502000012',
    address: address('Ogrodowa', '16', null, '85-001', 'Bydgoszcz'), // no apartment
  },
  {
    email: 'magdalena.wojcik@example.com',
    firstName: 'Magdalena',
    lastName: 'Wójcik',
    pesel: '87081440862',
    phoneNumber: '+48502000013',
    address: address('Szkolna', '3', '11', '35-001', 'Rzeszów'),
    lastLoginAt: '2026-11-06T07:45:00Z',
  },
  {
    email: 'olena.shevchenko@example.com',
    firstName: 'Olena',
    lastName: 'Shevchenko',
    pesel: '98030951542',
    phoneNumber: '+380501234567', // foreign phone number
    address: address('Mickiewicza', '27', '5', '45-367', 'Opole'),
  },
  {
    email: 'ab@example.com', // too short for the "password contains email" rule
    firstName: 'Ala',
    lastName: 'Bąk',
    pesel: '02292162288',
    phoneNumber: '+48502000014',
    address: address('Krótka', '1', null, '10-001', 'Olsztyn'),
  },
  {
    email: 'grzegorz.zajac@example.com',
    firstName: 'Grzegorz',
    lastName: 'Zając',
    pesel: '69113073934',
    phoneNumber: '+48502000015',
    address: address('Rynek', '7/9', null, '25-001', 'Kielce'), // building with a slash
  },
  {
    email: 'natalia.sikora@example.com',
    firstName: 'Natalia',
    lastName: 'Sikora',
    pesel: '94011684627',
    phoneNumber: '+48502000016',
    address: address('Zielona', '38', '20', '65-001', 'Zielona Góra'),
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
      // 10000002 and up. New registrations get a number outside this range.
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

const admin = (n: number, email: string, lastLoginAt: string | null): CurrentUserDto => ({
  id: uuid('c000', n),
  email,
  role: 'ADMIN',
  status: 'ACTIVE',
  lastLoginAt,
  customer: null,
});

const [ANNA, ...OTHER_CUSTOMERS] = MOCK_CUSTOMERS.map(toUser);

/** Log in with any of these emails + MOCK_PASSWORD. The first two are the usual customer and admin. */
export const MOCK_USERS: CurrentUserDto[] = [
  ANNA,
  admin(1, 'admin@triobank.example', null),
  admin(2, 'teller@triobank.example', '2026-11-05T08:30:00Z'),
  ...OTHER_CUSTOMERS,
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
