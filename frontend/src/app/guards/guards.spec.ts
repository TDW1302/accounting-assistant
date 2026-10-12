import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { AuthService } from '../services/auth.service';
import { testUser } from '../../testing/fixtures';

describe('Guards and interceptor', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  const runAuthGuard = () =>
    TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

  describe('authGuard', () => {
    it('lets a known user through without calling the API', () => {
      auth.currentUser.set(testUser());
      expect(runAuthGuard()).toBe(true);
    });

    it('restores the session, or sends to the login page', async () => {
      const allowed = firstValueFrom(runAuthGuard() as Observable<boolean>);
      http.expectOne('/api/auth/me').flush({ user: testUser(), passwordExpired: false });
      expect(await allowed).toBe(true);

      auth.currentUser.set(null);
      const refused = firstValueFrom(runAuthGuard() as Observable<UrlTree>);
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
      expect(router.serializeUrl(await refused)).toBe('/login');
    });
  });

  describe('roleGuard', () => {
    const run = () =>
      TestBed.runInInjectionContext(() => roleGuard(['ADMIN'])({} as never, {} as never));

    it('admits the listed roles and sends others home', () => {
      auth.currentUser.set(testUser({ role: 'ADMIN' }));
      expect(run()).toBe(true);

      auth.currentUser.set(testUser({ role: 'USER' }));
      expect(router.serializeUrl(run() as UrlTree)).toBe('/');
    });
  });

  describe('authInterceptor', () => {
    let navigate: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    });

    function call(url: string, status: number, body: object | null = null) {
      let failed = false;
      TestBed.inject(HttpClient)
        .get(url)
        .subscribe({ error: () => (failed = true) });
      const req = http.expectOne(url);
      expect(req.request.withCredentials).toBe(true);
      req.flush(body, { status, statusText: 'x' });
      return failed;
    }

    it('sends an expired session to the login page, except for auth calls', () => {
      expect(call('/api/invoices', 401)).toBe(true);
      expect(navigate).toHaveBeenCalledWith(['/login']);

      navigate.mockClear();
      call('/api/auth/me', 401);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('sends an expired password to the change-password page', () => {
      call('/api/invoices', 403, { passwordExpired: true });
      expect(navigate).toHaveBeenCalledWith(['/change-password']);

      navigate.mockClear();
      call('/api/invoices', 403, { error: 'Forbidden' });
      call('/api/invoices', 500);
      expect(navigate).not.toHaveBeenCalled();
    });
  });
});
