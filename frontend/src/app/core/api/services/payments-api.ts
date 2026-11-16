import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { CashOperationRequest, TransactionDto } from '../models';

/**
 * Money operations: T3, T4. Only HTTP calls, no UI logic.
 *
 * The caller makes one `idempotencyKey` (crypto.randomUUID()) per operation and sends the
 * same key again on retry. Then the backend books the money only once (BR-18).
 */
@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl;

  atmDeposit(request: CashOperationRequest, idempotencyKey: string) {
    return this.http.post<TransactionDto>(`${this.url}/atm/deposits`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  atmWithdrawal(request: CashOperationRequest, idempotencyKey: string) {
    return this.http.post<TransactionDto>(`${this.url}/atm/withdrawals`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
}
