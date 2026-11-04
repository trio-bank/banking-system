import { AccountDto } from './account.models';
import { IsoDate, IsoDateTime, Role, UserStatus, Uuid } from './common.models';

export interface AddressDto {
  street: string;
  buildingNumber: string;
  apartmentNumber: string | null;
  postalCode: string;
  city: string;
  countryCode: string | null; // always set in responses
}

// A1 POST /auth/register
export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  pesel: string;
  phoneNumber: string;
  address: AddressDto;
}

export interface RegistrationResponse {
  userId: Uuid;
  customerNumber: string;
  email: string;
  account: AccountDto;
}

// A2 POST /auth/login
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // seconds
  expiresAt: IsoDateTime;
  user: CurrentUserDto; // customer is always null here, full profile via GET /me
}

// A4 GET /me
export interface CurrentUserDto {
  id: Uuid;
  email: string;
  role: Role;
  status: UserStatus;
  lastLoginAt: IsoDateTime | null; // the previous login
  customer: CustomerProfileDto | null; // null for admins
}

export interface CustomerProfileDto {
  id: Uuid;
  customerNumber: string;
  firstName: string;
  lastName: string;
  peselMasked: string; // "*******2343"
  dateOfBirth: IsoDate;
  email: string;
  phoneNumber: string;
  address: AddressDto;
  createdAt: IsoDateTime;
}
