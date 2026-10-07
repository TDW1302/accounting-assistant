import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DangerZone } from './danger-zone/danger-zone';
import { SupplierMerge } from './supplier-merge/supplier-merge';
import { AdminService } from '../services/admin.service';
import { SupplierService } from '../services/supplier.service';
import { apiError, testSupplier } from '../../testing/fixtures';

describe('Admin screens', () => {
  let admin: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    admin = {
      getStats: vi.fn(() =>
        of({ supplierCount: 3, years: [{ year: 2026, invoiceCount: 4 }, { year: 2025, invoiceCount: 9 }] }),
      ),
      deleteInvoicesByYear: vi.fn(() => of(undefined)),
      deleteAllSuppliers: vi.fn(() => of(undefined)),
      findDuplicateSuppliers: vi.fn(() => of({ suppliers: 2, pairs: [], withoutDocument: [] })),
      mergeSuppliers: vi.fn(() =>
        of({ keptId: 1, keptName: 'Ondes', removedName: 'Ondes SA', invoicesReassigned: 2, fieldsFilled: [] }),
      ),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AdminService, useValue: admin },
        {
          provide: SupplierService,
          useValue: {
            list: vi.fn(() =>
              of([testSupplier({ id: 1, name: 'Ondes' }), testSupplier({ id: 2, name: 'Ondes SA' })]),
            ),
          },
        },
      ],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  describe('DangerZone', () => {
    let zone: DangerZone;

    beforeEach(() => {
      const fixture = TestBed.createComponent(DangerZone);
      fixture.detectChanges();
      zone = fixture.componentInstance;
    });

    it('preselects the latest year', () => {
      expect(zone.stats()?.supplierCount).toBe(3);
      expect(zone.selectedYear()).toBe(2026);

      // Un choix explicite n'est pas ecrase au rechargement.
      zone.selectedYear.set(2025);
      zone.load();
      expect(zone.selectedYear()).toBe(2025);
    });

    it('purges a year only once the confirmation word is typed', () => {
      zone.invoiceConfirmText.set('supprimer');
      zone.deleteInvoicesForYear();
      expect(admin['deleteInvoicesByYear']).not.toHaveBeenCalled();

      zone.invoiceConfirmText.set('SUPPRIMER');
      zone.deleteInvoicesForYear();
      expect(admin['deleteInvoicesByYear']).toHaveBeenCalledWith(2026);
      expect(zone.invoiceConfirmText()).toBe('');
      expect(zone.busy()).toBe(false);

      admin['deleteInvoicesByYear'].mockReturnValue(throwError(() => apiError('No invoices found')));
      zone.invoiceConfirmText.set('SUPPRIMER');
      zone.deleteInvoicesForYear();
      expect(zone.error()).toBe('No invoices found');

      admin['deleteInvoicesByYear'].mockReturnValue(throwError(() => apiError()));
      zone.deleteInvoicesForYear();
      expect(zone.error()).toContain('factures');
    });

    it('needs a year to purge', () => {
      admin['getStats'].mockReturnValue(of({ supplierCount: 0, years: [] }));
      zone.selectedYear.set(null);
      zone.load();
      zone.invoiceConfirmText.set('SUPPRIMER');

      zone.deleteInvoicesForYear();

      expect(zone.selectedYear()).toBeNull();
      expect(admin['deleteInvoicesByYear']).not.toHaveBeenCalled();
    });

    it('purges suppliers only once the confirmation word is typed', () => {
      zone.deleteAllSuppliers();
      expect(admin['deleteAllSuppliers']).not.toHaveBeenCalled();

      zone.supplierConfirmText.set('SUPPRIMER');
      zone.deleteAllSuppliers();
      expect(admin['deleteAllSuppliers']).toHaveBeenCalled();
      expect(zone.supplierConfirmText()).toBe('');

      admin['deleteAllSuppliers'].mockReturnValue(throwError(() => apiError('Invoices still exist')));
      zone.supplierConfirmText.set('SUPPRIMER');
      zone.deleteAllSuppliers();
      expect(zone.error()).toBe('Invoices still exist');

      admin['deleteAllSuppliers'].mockReturnValue(throwError(() => apiError()));
      zone.deleteAllSuppliers();
      expect(zone.error()).toContain('fournisseurs');
    });
  });

  describe('SupplierMerge', () => {
    let merge: SupplierMerge;
    let confirmSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
      const fixture = TestBed.createComponent(SupplierMerge);
      fixture.detectChanges();
      merge = fixture.componentInstance;
    });

    it('loads suppliers and duplicate candidates', () => {
      expect(merge.suppliers()).toHaveLength(2);
      expect(merge.duplicates()?.suppliers).toBe(2);
      expect(merge.categoryLabel('TELECOM')).toBe('Télécom');
      expect(merge.categoryLabel(null)).toBe('');
    });

    it('reports a failed detection', () => {
      admin['findDuplicateSuppliers'].mockReturnValue(throwError(() => apiError('boom')));
      merge.loadDuplicates();
      expect(merge.error()).toBe('boom');

      admin['findDuplicateSuppliers'].mockReturnValue(throwError(() => apiError()));
      merge.loadDuplicates();
      expect(merge.error()).toContain('doublons');
      expect(merge.loadingDuplicates()).toBe(false);
    });

    it('only merges two distinct records, after confirmation', () => {
      expect(merge.canMerge()).toBe(false);
      merge.merge();

      merge.selectPair(1, 1);
      expect(merge.canMerge()).toBe(false);

      merge.selectPair(1, 2);
      expect(merge.keepSupplier()?.name).toBe('Ondes');
      expect(merge.removeSupplier()?.name).toBe('Ondes SA');
      expect(merge.canMerge()).toBe(true);

      confirmSpy.mockReturnValueOnce(false);
      merge.merge();
      expect(admin['mergeSuppliers']).not.toHaveBeenCalled();

      merge.merge();
      expect(admin['mergeSuppliers']).toHaveBeenCalledWith(1, 2);
      expect(merge.lastMerge()?.invoicesReassigned).toBe(2);
      expect(merge.keepId()).toBeNull();
      expect(merge.keepSupplier()).toBeNull();
    });

    it('does not merge twice at once and reports failures', () => {
      merge.selectPair(1, 2);
      merge.busy.set(true);
      merge.merge();
      expect(admin['mergeSuppliers']).not.toHaveBeenCalled();

      merge.busy.set(false);
      admin['mergeSuppliers'].mockReturnValue(throwError(() => apiError('Supplier not found')));
      merge.merge();
      expect(merge.error()).toBe('Supplier not found');

      admin['mergeSuppliers'].mockReturnValue(throwError(() => apiError()));
      merge.merge();
      expect(merge.error()).toBe('Erreur lors de la fusion.');
      expect(merge.busy()).toBe(false);
    });

    it('an unknown id selects nobody', () => {
      merge.selectPair(1, 99);
      expect(merge.removeSupplier()).toBeNull();
    });
  });
});
