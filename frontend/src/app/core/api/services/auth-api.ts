import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import {
  CurrentUserDto,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegistrationResponse,
} from '../models';

/** Auth endpoints A1–A4. Only HTTP calls, no UI logic. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl;

  register(request: RegisterRequest) {
    return this.http.post<RegistrationResponse>(`${this.url}/auth/register`, request);
  }

  login(request: LoginRequest) {
    return this.http.post<LoginResponse>(`${this.url}/auth/login`, request);
  }

  logout() {
    return this.http.post<void>(`${this.url}/auth/logout`, null);
  }

  me() {
    return this.http.get<CurrentUserDto>(`${this.url}/me`);
  }
}
