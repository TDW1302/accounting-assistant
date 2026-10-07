import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Login } from './login/login';
import { ChangePassword } from './change-password/change-password';
import { AuthService } from '../services/auth.service';
import { ConfigService } from '../services/config.service';
import { isPasswordValid } from '../models/password-policy';
import { testUser } from '../../testing/fixtures';

describe('Auth screens', () => {
  let auth: Record<string, unknown> & {
    login: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
    updateAiProvider: ReturnType<typeof vi.fn>;
    currentUser: ReturnType<typeof vi.fn>;
    passwordExpired: ReturnType<typeof vi.fn>;
  };
  let config: { loadConfig: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    auth = {
      login: vi.fn(() => of({ user: testUser(), passwordExpired: false })),
      changePassword: vi.fn(() => of(undefined)),
      updateAiProvider: vi.fn(() => of(undefined)),
      currentUser: vi.fn(() => testUser({ aiProvider: 'GEMINI' })),
      passwordExpired: vi.fn(() => true),
    };
    config = { loadConfig: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ConfigService, useValue: config },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  describe('Login', () => {
    let login: Login;

    beforeEach(() => {
      const fixture = TestBed.createComponent(Login);
      fixture.detectChanges();
      login = fixture.componentInstance;
      login.username = 'alice';
      login.password = 'secret';
    });

    it('opens the application and loads its configuration', () => {
      login.login();
      expect(auth.login).toHaveBeenCalledWith({ username: 'alice', password: 'secret' });
      expect(config.loadConfig).toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/']);
    });

    it('sends an expired password straight to the change screen', () => {
      auth.login.mockReturnValue(of({ user: testUser(), passwordExpired: true }));
      login.login();
      expect(navigate).toHaveBeenCalledWith(['/change-password']);
    });

    it('says the same thing whatever went wrong', () => {
      auth.login.mockReturnValue(throwError(() => new Error('401')));
      login.login();
      expect(login.error).toBe("Nom d'utilisateur ou mot de passe incorrect");
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('ChangePassword', () => {
    let screen: ChangePassword;

    function create() {
      const fixture = TestBed.createComponent(ChangePassword);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    beforeEach(() => {
      screen = create();
    });

    it('starts from the user preference and the expiration state', () => {
      expect(screen.selectedProvider).toBe('GEMINI');
      expect(screen.isExpired).toBe(true);
    });

    it('defaults to Claude without user or preference', () => {
      auth.currentUser.mockReturnValue(testUser({ aiProvider: undefined }));
      expect(create().selectedProvider).toBe('CLAUDE');
      auth.currentUser.mockReturnValue(null);
      expect(create().selectedProvider).toBe('CLAUDE');
    });

    it('checks the new password against the policy', () => {
      screen.newPassword = 'short';
      expect(screen.isNewPasswordValid).toBe(false);
      screen.newPassword = 'Str0ng!Pass';
      expect(screen.isNewPasswordValid).toBe(true);
      expect(isPasswordValid('x'.repeat(129) + 'A1!')).toBe(false);
    });

    it('changes the password then goes home', () => {
      vi.useFakeTimers();
      screen.currentPassword = 'old';
      screen.newPassword = 'Str0ng!Pass';

      screen.changePassword();

      expect(screen.success).toBe(true);
      expect(screen.newPassword).toBe('');
      expect(navigate).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1500);
      expect(navigate).toHaveBeenCalledWith(['/']);
    });

    it('shows why the password was refused', () => {
      auth.changePassword.mockReturnValue(throwError(() => ({ error: { error: 'Current password is incorrect' } })));
      screen.changePassword();
      expect(screen.error).toBe('Current password is incorrect');

      auth.changePassword.mockReturnValue(throwError(() => ({ error: null })));
      screen.changePassword();
      expect(screen.error).toBe('Erreur lors du changement de mot de passe');
    });

    it('saves the AI provider', () => {
      screen.selectedProvider = 'CLAUDE';
      screen.saveProvider();
      expect(auth.updateAiProvider).toHaveBeenCalledWith({ aiProvider: 'CLAUDE' });
      expect(screen.providerSaved).toBe(true);

      auth.updateAiProvider.mockReturnValue(throwError(() => ({ error: { error: 'Invalid' } })));
      screen.saveProvider();
      expect(screen.providerError).toBe('Invalid');

      auth.updateAiProvider.mockReturnValue(throwError(() => ({ error: {} })));
      screen.saveProvider();
      expect(screen.providerError).toBe('Erreur lors de la sauvegarde');
    });
  });
});
