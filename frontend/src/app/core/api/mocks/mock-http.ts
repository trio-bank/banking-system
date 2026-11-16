import { HttpErrorResponse, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { FieldError, ProblemDetail } from '../models';

/** A mock returns either a normal response or an error, like a real server. */
export type MockResult = HttpResponse<unknown> | HttpErrorResponse;
/** `params` = values from the path, e.g. `{ id: '7d1e…' }` for `/accounts/:id`. */
export type MockHandler = (req: HttpRequest<unknown>, params: Record<string, string>) => MockResult;

/** Current time (+ offset) in the API format: `2026-10-24T13:02:11Z`, no milliseconds. */
export function isoNow(offsetSeconds = 0): string {
  return new Date(Date.now() + offsetSeconds * 1000).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

export function ok(
  req: HttpRequest<unknown>,
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): HttpResponse<unknown> {
  return new HttpResponse({ status, body, url: req.url, headers: new HttpHeaders(headers) });
}

/** Error in the same Problem Details format as the backend. */
export function problem(
  req: HttpRequest<unknown>,
  status: number,
  code: string,
  title: string,
  detail: string,
  extra: Partial<ProblemDetail> = {},
): HttpErrorResponse {
  const body: ProblemDetail = {
    type: `https://triobank.example/errors/${code.toLowerCase().replaceAll('_', '-')}`,
    title,
    status,
    detail,
    instance: req.url,
    code,
    traceId: 'mock',
    timestamp: isoNow(),
    errors: [],
    ...extra,
  };
  return new HttpErrorResponse({
    status,
    statusText: title,
    url: req.url,
    headers: new HttpHeaders({ 'Content-Type': 'application/problem+json' }),
    error: body,
  });
}

/** 400 VALIDATION_ERROR for one field, like docs/05 §3. */
export function validationError(
  req: HttpRequest<unknown>,
  field: string,
  code: string,
  message: string,
): HttpErrorResponse {
  const errors: FieldError[] = [{ field, code, message }];
  return problem(
    req,
    400,
    'VALIDATION_ERROR',
    'Validation failed',
    'One or more fields are invalid.',
    {
      errors,
    },
  );
}

export const unauthenticated = (req: HttpRequest<unknown>) =>
  problem(req, 401, 'UNAUTHENTICATED', 'Unauthenticated', 'Missing or invalid token.');

export const notFound = (req: HttpRequest<unknown>, what: string) =>
  problem(req, 404, 'RESOURCE_NOT_FOUND', 'Resource not found', `${what} not found.`);
