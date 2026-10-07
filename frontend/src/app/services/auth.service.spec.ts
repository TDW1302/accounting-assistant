import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { testUser } from '../../testing/fixtures';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the session user from login until logout', () => {
    service.login({ username: 'alice', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush({ user: testUser(), passwordExpired: true });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.passwordExpired()).toBe(true);
    expect(service.hasRole('USER')).toBe(true);
    expect(service.hasAnyRole('ADMIN', 'USER')).toBe(true);
    expect(service.hasAnyRole('ADMIN')).toBe(false);

    service.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);

    expect(service.isAuthenticated()).toBe(false);
    expect(service.passwordExpired()).toBe(false);
    expect(service.hasAnyRole('USER')).toBe(false);
    expect(service.hasRole('USER')).toBe(false);
  });

  it('restores the user from the session', () => {
    service.getCurrentUser().subscribe();
    http.expectOne('/api/auth/me').flush({ user: testUser({ role: 'ADMIN' }), passwordExpired: false });

    expect(service.hasRole('ADMIN')).toBe(true);
  });

  it('clears the expiration once the password is changed', () => {
    service.passwordExpired.set(true);
    service.changePassword({ currentPassword: 'a', newPassword: 'b' }).subscribe();
    http.expectOne('/api/auth/change-password').flush(null);

    expect(service.passwordExpired()).toBe(false);
  });

  it('updates the AI provider of the current user only when there is one', () => {
    service.updateAiProvider({ aiProvider: 'GEMINI' }).subscribe();
    http.expectOne('/api/auth/ai-provider').flush(null);
    expect(service.currentUser()).toBeNull();

    service.currentUser.set(testUser());
    service.updateAiProvider({ aiProvider: 'GEMINI' }).subscribe();
    http.expectOne('/api/auth/ai-provider').flush(null);
    expect(service.currentUser()?.aiProvider).toBe('GEMINI');
  });
});
