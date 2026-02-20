import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  User, UserRole, LoginRequest, RegisterRequest,
  ChangePasswordRequest, AuthResponse
} from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/auth';

  readonly currentUser = signal<User | null>(null);
  readonly passwordExpired = signal<boolean>(false);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.url}/login`, request).pipe(
      tap(response => {
        this.currentUser.set(response.user);
        this.passwordExpired.set(response.passwordExpired);
      })
    );
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.url}/register`, request);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.url}/logout`, {}).pipe(
      tap(() => {
        this.currentUser.set(null);
        this.passwordExpired.set(false);
      })
    );
  }

  getCurrentUser(): Observable<AuthResponse> {
    return this.http.get<AuthResponse>(`${this.url}/me`).pipe(
      tap(response => {
        this.currentUser.set(response.user);
        this.passwordExpired.set(response.passwordExpired);
      })
    );
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.url}/change-password`, request).pipe(
      tap(() => this.passwordExpired.set(false))
    );
  }

  hasRole(role: UserRole): boolean {
    return this.currentUser()?.role === role;
  }

  hasAnyRole(...roles: UserRole[]): boolean {
    const userRole = this.currentUser()?.role;
    return userRole ? roles.includes(userRole) : false;
  }
}
