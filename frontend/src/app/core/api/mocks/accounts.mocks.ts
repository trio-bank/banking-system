import { HttpParams, HttpRequest } from '@angular/common/http';
import {
  AccountDto,
  Direction,
  OpenAccountRequest,
  PageResponse,
  RenameAccountRequest,
} from '../models';
import { accountEntries, addAccount, findAccount, MOCK_PRODUCTS, mockAccounts } from './mock-bank';
import { isoNow, MockHandler, notFound, ok, problem, validationError } from './mock-http';

const MAX_NAME = 50; // BR-28
const MAX_PAGE_SIZE = 100;

/** Copy, so a page can't change the mock state by accident. */
const copy = (account: AccountDto): AccountDto => ({ ...account });

/** P1 */
export const products: MockHandler = (req) => ok(req, MOCK_PRODUCTS);

/** C1. Personal first, then by opening date (docs/05 C1). */
export const listAccounts: MockHandler = (req) => {
  const sorted = [...mockAccounts()].sort(
    (a, b) =>
      Number(b.type === 'PERSONAL') - Number(a.type === 'PERSONAL') ||
      a.openedAt.localeCompare(b.openedAt),
  );
  return ok(req, sorted.map(copy));
};

/**
 * C2. A 3rd personal account → ACCOUNT_LIMIT_REACHED (the seed already has 2).
 * Unknown product code → 404.
 */
export const openAccount: MockHandler = (req) => {
  const body = req.body as OpenAccountRequest;
  const name = body.name?.trim() ?? '';
  if (name.length > MAX_NAME) {
    return validationError(
      req,
      'name',
      'TOO_LONG',
      `Name can have at most ${MAX_NAME} characters.`,
    );
  }
  const product = MOCK_PRODUCTS.find((p) => p.code === body.productCode);
  if (!product) {
    return notFound(req, 'Product');
  }
  const open = mockAccounts().filter(
    (account) => account.productCode === product.code && account.status !== 'CLOSED',
  );
  if (open.length >= product.maxAccountsPerCustomer) {
    return problem(
      req,
      422,
      'ACCOUNT_LIMIT_REACHED',
      'Account limit reached',
      `You can have at most ${product.maxAccountsPerCustomer} accounts of this product.`,
    );
  }
  const hasPrerequisite =
    product.prerequisiteType === null ||
    mockAccounts().some(
      (account) => account.type === product.prerequisiteType && account.status === 'ACTIVE',
    );
  if (!hasPrerequisite) {
    return problem(
      req,
      422,
      'PREREQUISITE_ACCOUNT_REQUIRED',
      'Prerequisite account required',
      `This product needs an active ${product.prerequisiteType} account.`,
    );
  }
  // Blank name → the product name, as the backend does (docs/03 §7).
  const account = addAccount(product, name || product.name, isoNow());
  return ok(req, copy(account), 201, { Location: `/api/v1/accounts/${account.id}` });
};

/** C3 */
export const getAccount: MockHandler = (req, { id }) => {
  const account = findAccount(id);
  return account ? ok(req, copy(account)) : notFound(req, 'Account');
};

/** C4 */
export const renameAccount: MockHandler = (req, { id }) => {
  const account = findAccount(id);
  if (!account) {
    return notFound(req, 'Account');
  }
  const name = (req.body as RenameAccountRequest).name?.trim() ?? '';
  if (!name) {
    return validationError(req, 'name', 'REQUIRED', 'Name is required.');
  }
  if (name.length > MAX_NAME) {
    return validationError(
      req,
      'name',
      'TOO_LONG',
      `Name can have at most ${MAX_NAME} characters.`,
    );
  }
  account.name = name;
  return ok(req, copy(account));
};

/** C5. Filters and pages like the backend. Dates are compared in Warsaw time. */
export const accountHistory: MockHandler = (req, { id }) => {
  if (!findAccount(id)) {
    return notFound(req, 'Account');
  }
  const query = queryOf(req);
  const from = query.get('from');
  const to = query.get('to');
  const direction = query.get('direction') as Direction | null;
  const types = query.getAll('type') ?? [];
  const page = Number(query.get('page') ?? 0);
  const size = Number(query.get('size') ?? 20);

  if (from && to && from > to) {
    return validationError(req, 'from', 'INVALID_DATE', '"from" must not be after "to".');
  }
  if (!Number.isInteger(size) || size < 1 || size > MAX_PAGE_SIZE) {
    return validationError(req, 'size', 'OUT_OF_RANGE', `Size must be 1–${MAX_PAGE_SIZE}.`);
  }
  if (!Number.isInteger(page) || page < 0) {
    return validationError(req, 'page', 'OUT_OF_RANGE', 'Page must be 0 or more.');
  }

  const items = accountEntries(id).filter((item) => {
    const day = warsawDate(item.createdAt);
    return (
      (!from || day >= from) &&
      (!to || day <= to) &&
      (!direction || item.direction === direction) &&
      (types.length === 0 || types.includes(item.type))
    );
  });
  const response: PageResponse<(typeof items)[number]> = {
    content: items.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  };
  return ok(req, response);
};

/** Query params, whether they came in `params` or were written into the URL. */
function queryOf(req: HttpRequest<unknown>): HttpParams {
  return new HttpParams({ fromString: req.urlWithParams.split('?')[1] ?? '' });
}

/** "2026-10-31T23:30:00Z" → "2026-11-01" (in Warsaw it's already the next day). */
const warsawDate = (timestamp: string) =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Warsaw' }).format(new Date(timestamp));
