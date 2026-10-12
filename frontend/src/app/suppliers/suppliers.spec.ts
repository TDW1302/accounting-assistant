import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SupplierList } from './supplier-list/supplier-list';
import { SupplierForm } from './supplier-form/supplier-form';
import { SupplierService } from '../services/supplier.service';
import { testSupplier } from '../../testing/fixtures';

describe('Supplier screens', () => {
  let suppliers: Record<string, ReturnType<typeof vi.fn>>;
  let navigate: ReturnType<typeof vi.spyOn>;

  function configure(params: Record<string, string> = {}) {
    suppliers = {
      list: vi.fn(() =>
        of([
          testSupplier({ id: 1, name: 'zeta', alias: null, category: 'TELECOM' }),
          testSupplier({ id: 2, name: 'Alpha', alias: 'B', category: null }),
          testSupplier({ id: 3, name: 'beta', alias: 'A', category: 'AUTRE' }),
          testSupplier({ id: 4, name: 'Gamma', alias: 'A', category: 'AUTRE' }),
        ]),
      ),
      get: vi.fn(() => of(testSupplier({ id: 5, name: 'Ondes', defaultPeppol: true }))),
      create: vi.fn(() => of(testSupplier())),
      update: vi.fn(() => of(testSupplier())),
      delete: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SupplierService, useValue: suppliers },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params) } } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  }

  afterEach(() => vi.restoreAllMocks());

  describe('SupplierList', () => {
    let list: SupplierList;

    beforeEach(() => {
      configure();
      const fixture = TestBed.createComponent(SupplierList);
      fixture.detectChanges();
      list = fixture.componentInstance;
    });

    const names = () => list.sortedSuppliers().map(s => s.name);

    it('sorts by name, then by any column, empty values always last', () => {
      expect(names()).toEqual(['Alpha', 'beta', 'Gamma', 'zeta']);
      expect(list.sortIndicator('name')).toBe(' ▲');
      expect(list.sortIndicator('alias')).toBe('');

      list.sortBy('name');
      expect(names()).toEqual(['zeta', 'Gamma', 'beta', 'Alpha']);
      expect(list.sortIndicator('name')).toBe(' ▼');

      list.sortBy('alias');
      // A-A departages par le nom, sans alias en dernier.
      expect(names()).toEqual(['beta', 'Gamma', 'Alpha', 'zeta']);

      list.sortBy('category');
      expect(names()).toEqual(['beta', 'Gamma', 'zeta', 'Alpha']);
      list.sortBy('category');
      expect(names()).toEqual(['zeta', 'beta', 'Gamma', 'Alpha']);
    });

    it('labels categories and scopes', () => {
      expect(list.categoryLabel('TELECOM')).toBe('Télécom');
      expect(list.categoryLabel(null)).toBe('');
      expect(list.scopeLabel('MONTHLY')).toBe('Mensuelle');
      expect(list.scopeLabel(null)).toBe('');
    });

    it('filters by category', () => {
      list.categoryFilter = 'TELECOM';
      list.load();
      expect(suppliers['list']).toHaveBeenLastCalledWith('TELECOM');
    });

    it('opens and deletes a supplier after confirmation', () => {
      list.openSupplier(testSupplier({ id: 3 }));
      expect(navigate).toHaveBeenCalledWith(['/suppliers', 3, 'edit']);

      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      list.deleteSupplier(testSupplier());
      expect(suppliers['delete']).not.toHaveBeenCalled();

      confirmSpy.mockReturnValue(true);
      list.deleteSupplier(testSupplier());
      expect(suppliers['list']).toHaveBeenCalledTimes(2);

      suppliers['delete'].mockReturnValue(throwError(() => new Error('x')));
      list.deleteSupplier(testSupplier());
      expect(alertSpy).toHaveBeenCalled();
    });
  });

  describe('SupplierForm', () => {
    function create(params: Record<string, string> = {}) {
      configure(params);
      const fixture = TestBed.createComponent(SupplierForm);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    it('creates a supplier with empty fields sent as null', () => {
      const form = create();
      form.save();
      expect(suppliers['create']).not.toHaveBeenCalled();

      form.form.patchValue({ name: 'Ondes', alias: '', category: '' });
      form.save();
      expect(suppliers['create']).toHaveBeenCalledWith({
        name: 'Ondes',
        alias: null,
        enterpriseNumber: null,
        category: null,
        defaultDateScope: null,
        defaultPeppol: false,
      });
      expect(navigate).toHaveBeenCalledWith(['/suppliers']);
    });

    it('edits an existing supplier', () => {
      const form = create({ id: '5' });
      expect(form.isEdit).toBe(true);
      expect(form.form.value.defaultPeppol).toBe(true);

      form.save();
      expect(suppliers['update']).toHaveBeenCalledWith(
        5,
        expect.objectContaining({ name: 'Ondes', alias: 'Ondes', category: 'TELECOM', defaultPeppol: true }),
      );
    });
  });
});
