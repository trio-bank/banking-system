import { HttpRequest } from '@angular/common/http';
import {
  CurrentUserDto,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegistrationResponse,
} from '../models';
import { isUnderage } from '../../../shared/validators';
import { MOCK_CUSTOMERS, MOCK_PASSWORD, MOCK_USERS, NEW_PERSONAL_ACCOUNT } from './mock-data';
import { isoNow, MockHandler, MockResult, ok, problem } from './mock-http';

const TOKEN_PREFIX = 'mock-token.';
const SESSION_SECONDS = 30 * 60;
const LOCK_SECONDS = 15 * 60;

const findUser = (email: string) =>
  MOCK_USERS.find((user) => user.email === email.trim().toLowerCase());

/** Finds the user by the `Authorization: Bearer mock-token.<userId>` header. */
function currentUser(req: HttpRequest<unknown>): CurrentUserDto | undefined {
  const userId = req.headers.get('Authorization')?.replace(`Bearer ${TOKEN_PREFIX}`, '');
  return MOCK_USERS.find((user) => user.id === userId);
}

const unauthenticated = (req: HttpRequest<unknown>) =>
  problem(req, 401, 'UNAUTHENTICATED', 'Unauthenticated', 'Missing or invalid token.');

/** A1. Any new email and PESEL work; taken ones give 409, a person under 18 gives 422. */
export const register: MockHandler = (req) => {
  const body = req.body as RegisterRequest;
  if (findUser(body.email)) {
    return problem(
      req,
      409,
      'EMAIL_ALREADY_REGISTERED',
      'Email already registered',
      'A user with this email already exists.',
    );
  }
  if (MOCK_CUSTOMERS.some((customer) => customer.pesel === body.pesel)) {
    return problem(
      req,
      409,
      'PESEL_ALREADY_REGISTERED',
      'PESEL already registered',
      'A customer with this PESEL already exists.',
    );
  }
  if (isUnderage(body.pesel)) {
    return problem(
      req,
      422,
      'CUSTOMER_UNDERAGE',
      'Customer underage',
      'The customer must be at least 18 years old.',
    );
  }
  const response: RegistrationResponse = {
    userId: '0b6f2a3e-8f44-4f0e-9a51-2c1e5d7b9a11',
    customerNumber: '10000100',
    email: body.email.trim().toLowerCase(),
    account: { ...NEW_PERSONAL_ACCOUNT, openedAt: isoNow() },
  };
  return ok(req, response, 201, { Location: '/api/v1/me' });
};

/** A2. Same check order as the backend: locked/blocked wins even with the right password. */
export const login: MockHandler = (req) => {
  const body = req.body as LoginRequest;
  const user = findUser(body.email);
  if (user?.status === 'LOCKED') {
    return problem(req, 403, 'USER_LOCKED', 'User locked', 'Too many failed login attempts.', {
      lockedUntil: isoNow(LOCK_SECONDS),
    });
  }
  if (user?.status === 'BLOCKED') {
    return problem(req, 403, 'USER_BLOCKED', 'User blocked', 'The user is blocked by an admin.');
  }
  if (!user || body.password !== MOCK_PASSWORD) {
    return problem(
      req,
      401,
      'INVALID_CREDENTIALS',
      'Invalid credentials',
      'Wrong email or password.',
    );
  }
  const response: LoginResponse = {
    accessToken: TOKEN_PREFIX + user.id,
    tokenType: 'Bearer',
    expiresIn: SESSION_SECONDS,
    expiresAt: isoNow(SESSION_SECONDS),
    user: { ...user, customer: null },
  };
  return ok(req, response);
};

/** A3 */
export const logout: MockHandler = (req): MockResult =>
  currentUser(req) ? ok(req, null, 204) : unauthenticated(req);

/** A4 */
export const me: MockHandler = (req) => {
  const user = currentUser(req);
  return user ? ok(req, user) : unauthenticated(req);
};
