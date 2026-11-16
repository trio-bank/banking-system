import { HttpClient, HttpParams } from '@angular/common/http';
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
      { params: toParams(query) },
    );
  }
}

/** Skips empty values. `type: ['A', 'B']` becomes `?type=A&type=B`, as the backend expects. */
function toParams(query: AccountHistoryQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    for (const item of [value].flat()) {
      if (item !== undefined && item !== null && item !== '') {
        params = params.append(key, String(item));
      }
    }
  }
  return params;
}
