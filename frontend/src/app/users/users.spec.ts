import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserList } from './user-list/user-list';
import { UserForm } from './user-form/user-form';
import { UserService } from '../services/user.service';
import { testUser } from '../../testing/fixtures';

describe('User screens', () => {
  let users: Record<string, ReturnType<typeof vi.fn>>;
  let navigate: ReturnType<typeof vi.spyOn>;

  function configure(params: Record<string, string> = {}) {
    users = {
      list: vi.fn(() => of([testUser()])),
      get: vi.fn(() => of(testUser({ id: 4, username: 'bob', role: 'VIEWER', enabled: false }))),
      create: vi.fn(() => of(testUser())),
      update: vi.fn(() => of(testUser())),
      delete: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: UserService, useValue: users },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params) } } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  }

  afterEach(() => vi.restoreAllMocks());

  describe('UserList', () => {
    it('lists, opens and deletes users after confirmation', () => {
      configure();
      const fixture = TestBed.createComponent(UserList);
      fixture.detectChanges();
      const list = fixture.componentInstance;
      expect(list.users()).toHaveLength(1);

      list.openUser(testUser({ id: 4 }));
      expect(navigate).toHaveBeenCalledWith(['/users', 4, 'edit']);

      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      list.deleteUser(testUser());
      expect(users['delete']).not.toHaveBeenCalled();

      confirmSpy.mockReturnValue(true);
      list.deleteUser(testUser());
      expect(users['list']).toHaveBeenCalledTimes(2);

      users['delete'].mockReturnValue(throwError(() => new Error('x')));
      list.deleteUser(testUser());
      expect(alertSpy).toHaveBeenCalled();
    });
  });

  describe('UserForm', () => {
    function create(params: Record<string, string> = {}) {
      configure(params);
      const fixture = TestBed.createComponent(UserForm);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    it('enforces the password policy on creation', () => {
      const form = create();
      form.form.patchValue({ username: 'carol', email: 'carol@test.local', password: 'weakpass' });
      expect(form.form.valid).toBe(false);

      form.form.patchValue({ password: 'Str0ng!Pass' });
      expect(form.form.valid).toBe(true);
      form.save();
      expect(users['create']).toHaveBeenCalledWith({
        username: 'carol',
        email: 'carol@test.local',
        password: 'Str0ng!Pass',
        role: 'VIEWER',
        enabled: true,
      });
      expect(navigate).toHaveBeenCalledWith(['/users']);
    });

    it('edits an account without username nor password', () => {
      const form = create({ id: '4' });
      expect(form.isEdit).toBe(true);
      expect(form.form.get('username')!.disabled).toBe(true);
      expect(form.form.get('password')!.disabled).toBe(true);
      expect(form.form.getRawValue().username).toBe('bob');

      form.save();
      expect(users['update']).toHaveBeenCalledWith(4, {
        email: 'alice@test.local',
        role: 'VIEWER',
        enabled: false,
      });
    });

    it('shows the API message, its validation errors, or a fallback', () => {
      const form = create({ id: '4' });

      users['update'].mockReturnValue(throwError(() => ({ error: { error: 'Email already exists' } })));
      form.save();
      expect(form.error).toBe('Email already exists');

      users['update'].mockReturnValue(
        throwError(() => ({ error: { errors: { email: 'must be valid', role: 'required' } } })),
      );
      form.save();
      expect(form.error).toBe('must be valid required');

      users['update'].mockReturnValue(throwError(() => ({})));
      form.save();
      expect(form.error).toBe("Erreur lors de l'enregistrement");
    });

    it('reports a failed creation', () => {
      const form = create();
      users['create'].mockReturnValue(throwError(() => ({ error: { error: 'Username already exists' } })));
      form.save();
      expect(form.error).toBe('Username already exists');
    });
  });
});
