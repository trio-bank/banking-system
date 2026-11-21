import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import {
  AccountDto,
  AccountHistoryItemDto,
  AccountHistoryQuery,
  AccountProductDto,
  OpenAccountRequest,
  PageResponse,
  RenameAccountRequest,
  Uuid,
} from '../models';

/** Products and accounts: P1, C1–C5. Only HTTP calls, no UI logic. */
@Injectable({ providedIn: 'root' })
export class AccountsApi {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl;

  products() {
    return this.http.get<AccountProductDto[]>(`${this.url}/account-products`);
  }

  list() {
    return this.http.get<AccountDto[]>(`${this.url}/accounts`);
  }

  open(request: OpenAccountRequest) {
    return this.http.post<AccountDto>(`${this.url}/accounts`, request);
  }

  get(accountId: Uuid) {
    return this.http.get<AccountDto>(`${this.url}/accounts/${accountId}`);
  }

  rename(accountId: Uuid, request: RenameAccountRequest) {
    return this.http.patch<AccountDto>(`${this.url}/accounts/${accountId}`, request);
  }

  history(accountId: Uuid, query: AccountHistoryQuery = {}) {
    return this.http.get<PageResponse<AccountHistoryItemDto>>(
      `${this.url}/accounts/${accountId}/history`,
      { params: withoutUnset(query) },
    );
  }
}

/** Leaves out params that aren't set, so the backend uses its defaults. */
function withoutUnset(query: AccountHistoryQuery): Record<string, number> {
  return Object.fromEntries(
    Object.entries(query).filter((entry): entry is [string, number] => entry[1] !== undefined),
  );
}
