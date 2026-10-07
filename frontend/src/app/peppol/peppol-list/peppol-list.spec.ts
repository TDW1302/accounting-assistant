import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PeppolList } from './peppol-list';
import { PeppolService } from '../../services/peppol.service';
import { SupplierService } from '../../services/supplier.service';
import { PeppolDocument } from '../../models/peppol.model';
import { testInvoice, testSupplier } from '../../../testing/fixtures';

describe('PeppolList', () => {
  let peppol: Record<string, ReturnType<typeof vi.fn>>;
  let suppliers: Record<string, ReturnType<typeof vi.fn>>;
  let screen: PeppolList;

  const doc = (overrides: Partial<PeppolDocument> = {}): PeppolDocument => ({
    id: 'doc-1',
    receivedAt: '2026-03-01',
    invoiceDate: null,
    invoiceDueDate: null,
    amount: 121,
    senderName: 'Ondes SA',
    senderVatNumber: 'BE0456789034',
    currency: 'EUR',
    invoiceReference: null,
    isCreditNote: false,
    alreadyImported: false,
    matchedSupplierId: 1,
    matchedSupplierName: 'Ondes',
    ...overrides,
  });

  beforeEach(() => {
    peppol = {
      listInbound: vi.fn(() => of([doc()])),
      importDocument: vi.fn(() => of(testInvoice())),
      importSuppliers: vi.fn(() => of([])),
    };
    suppliers = {
      list: vi.fn(() =>
        of([
          testSupplier({ id: 1, defaultDateScope: 'YEARLY' }),
          testSupplier({ id: 2, defaultDateScope: null }),
        ]),
      ),
    };
    TestBed.configureTestingModule({
      imports: [PeppolList],
      providers: [
        { provide: PeppolService, useValue: peppol },
        { provide: SupplierService, useValue: suppliers },
      ],
    });
    const fixture = TestBed.createComponent(PeppolList);
    fixture.detectChanges();
    screen = fixture.componentInstance;
  });

  it('lists inbound documents with the filters that are set', () => {
    expect(peppol['listInbound']).toHaveBeenCalledWith({});
    expect(screen.documents()).toHaveLength(1);

    screen.receivedAfter = '2026-01-01';
    screen.receivedBefore = '2026-02-01';
    screen.senderName = ' Ond ';
    screen.load();
    expect(peppol['listInbound']).toHaveBeenLastCalledWith({
      receivedAfter: '2026-01-01',
      receivedBefore: '2026-02-01',
      senderName: 'Ond',
    });

    screen.resetFilters();
    expect(peppol['listInbound']).toHaveBeenLastCalledWith({});
  });

  it('reports a Falco outage', () => {
    peppol['listInbound'].mockReturnValue(throwError(() => new Error('x')));
    screen.load();
    expect(screen.error()).toContain('Peppol');
    expect(screen.loading()).toBe(false);
  });

  it('opens the import form with the matched supplier and its default scope', () => {
    screen.toggleImport(doc());
    expect(screen.expandedDocId).toBe('doc-1');
    expect(screen.importSupplierId).toBe(1);
    expect(screen.importDateScope).toBe('YEARLY');

    screen.toggleImport(doc());
    expect(screen.expandedDocId).toBeNull();

    screen.toggleImport(doc({ id: 'doc-2', matchedSupplierId: null }));
    expect(screen.importDateScope).toBe('MONTHLY');
  });

  it('follows the default scope of the supplier chosen by hand', () => {
    screen.importDateScope = 'DAILY';
    screen.importSupplierId = 2;
    screen.onImportSupplierChange();
    expect(screen.importDateScope).toBe('DAILY');

    screen.importSupplierId = 1;
    screen.onImportSupplierChange();
    expect(screen.importDateScope).toBe('YEARLY');
  });

  it('imports a document with the received date and amount', () => {
    screen.importSupplierId = null;
    screen.confirmImport(doc());
    expect(peppol['importDocument']).not.toHaveBeenCalled();

    screen.toggleImport(doc());
    screen.importComment = 'Fibre';
    screen.confirmImport(doc());
    expect(peppol['importDocument']).toHaveBeenCalledWith({
      falcoDocumentId: 'doc-1',
      supplierId: 1,
      year: new Date().getFullYear(),
      type: 'PURCHASE',
      dateScope: 'YEARLY',
      scopeDate: null,
      fileDetail: null,
      comment: 'Fibre',
      amountIncVat: 121,
      receptionDate: '2026-03-01',
    });
    expect(screen.expandedDocId).toBeNull();
    expect(screen.importing).toBe(false);

    peppol['importDocument'].mockReturnValue(throwError(() => new Error('x')));
    screen.importSupplierId = 1;
    screen.confirmImport(doc());
    expect(screen.error()).toContain('import du document');
  });

  it('imports the senders as suppliers, then refreshes', () => {
    screen.importSuppliers();
    expect(suppliers['list']).toHaveBeenCalledTimes(2);
    expect(screen.importingSuppliers).toBe(false);

    peppol['importSuppliers'].mockReturnValue(throwError(() => new Error('x')));
    screen.importSuppliers();
    expect(screen.error()).toContain('fournisseurs');
  });
});
