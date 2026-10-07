import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { App } from './app';
import { routes } from './app.routes';
import { appConfig } from './app.config';
import { AuthService } from './services/auth.service';
import { ConfigService } from './services/config.service';
import { periodicityLabel } from './models/recurring-expense.model';
import { dateScopeLabel } from './models/invoice.model';

describe('Application shell', () => {
  describe('App', () => {
    let auth: { getCurrentUser: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
    let config: { loadConfig: ReturnType<typeof vi.fn> };

    beforeEach(() => {
      auth = {
        getCurrentUser: vi.fn(() => of({})),
        logout: vi.fn(() => of(undefined)),
      };
      config = { loadConfig: vi.fn() };
      TestBed.configureTestingModule({
        imports: [App],
        providers: [
          provideRouter([]),
          {
            provide: AuthService,
            useValue: { ...auth, isAuthenticated: () => false, hasRole: () => false, currentUser: () => null },
          },
          {
            provide: ConfigService,
            useValue: { ...config, inboxErrorCount: () => 0, peppolEnabled: () => false },
          },
        ],
      });
    });

    it('restores the session and then loads the configuration', () => {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      expect(auth.getCurrentUser).toHaveBeenCalled();
      expect(config.loadConfig).toHaveBeenCalled();
    });

    it('stays quiet when nobody is logged in', () => {
      auth.getCurrentUser.mockReturnValue(throwError(() => ({ status: 401 })));
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      expect(config.loadConfig).not.toHaveBeenCalled();
    });

    it('logs out to the login page', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const fixture = TestBed.createComponent(App);
      fixture.componentInstance.logout();
      expect(auth.logout).toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('routes', () => {
    it('lazy-loads a standalone component for every page', async () => {
      const lazy = routes.filter(route => route.loadComponent);
      expect(lazy.length).toBeGreaterThan(15);
      for (const route of lazy) {
        const component = await route.loadComponent!();
        expect(component, route.path).toBeTruthy();
      }
    });

    it('guards every page but the login', () => {
      const unguarded = routes.filter(r => r.loadComponent && !r.canActivate).map(r => r.path);
      expect(unguarded).toEqual(['login']);
      expect(appConfig.providers.length).toBeGreaterThan(0);
    });
  });

  describe('labels', () => {
    it('falls back on an empty label for unknown values', () => {
      expect(periodicityLabel('MONTHLY')).toBe('Mensuelle');
      expect(periodicityLabel(null)).toBe('');
      expect(dateScopeLabel('YEARLY')).toBe('Annuelle');
      expect(dateScopeLabel(null)).toBe('');
    });
  });
});
