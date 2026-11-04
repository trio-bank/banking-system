import { HttpErrorResponse, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { ProblemDetail } from '../models';

/** A mock returns either a normal response or an error, like a real server. */
export type MockResult = HttpResponse<unknown> | HttpErrorResponse;
export type MockHandler = (req: HttpRequest<unknown>) => MockResult;

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
