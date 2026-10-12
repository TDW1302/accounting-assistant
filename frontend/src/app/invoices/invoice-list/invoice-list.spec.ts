import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { InvoiceList } from './invoice-list';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { ImportService } from '../../services/import.service';
import { InboxService } from '../../services/inbox.service';
import { ConfigService } from '../../services/config.service';
import { fileEvent, testInvoice, testSupplier } from '../../../testing/fixtures';

describe('InvoiceList', () => {
  let invoices: Record<string, ReturnType<typeof vi.fn>>;
  let importer: Record<string, ReturnType<typeof vi.fn>>;
  let inbox: Record<string, ReturnType<typeof vi.fn>>;
  let config: Record<string, ReturnType<typeof vi.fn>>;
  let list: InvoiceList;
  let alertSpy: ReturnType<typeof vi.spyOn>;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  const rows = [
    testInvoice({ id: 1, number: 2, year: 2026, amountIncVat: 10, amountExVat: null, vatAmount: null }),
    testInvoice({ id: 2, number: 1, year: 2026, subNumber: 2, type: 'SALE', amountIncVat: 20 }),
    testInvoice({ id: 3, number: 1, year: 2026, subNumber: 1, amountIncVat: null }),
    testInvoice({ id: 4, number: 1, year: 2026, series: 'EXPENSE', amountIncVat: 5 }),
    testInvoice({ id: 5, number: 9, year: 2025, amountIncVat: 1 }),
  ];

  beforeEach(() => {
    invoices = {
      list: vi.fn(() => of(rows)),
      search: vi.fn(() => of([rows[0]])),
      delete: vi.fn(() => of(undefined)),
    };
    importer = {
      importExcel: vi.fn(() => of({ suppliersCreated: 1, invoicesImported: 2, rowsSkipped: 0, warnings: [] })),
    };
    inbox = {
      scan: vi.fn(() => of({ filesProcessed: 1, matched: 1, created: 0, errors: 0, errorFiles: [] })),
    };
    config = { loadConfig: vi.fn() };
    TestBed.configureTestingModule({
      imports: [InvoiceList],
      providers: [
        provideRouter([]),
        { provide: InvoiceService, useValue: invoices },
        { provide: SupplierService, useValue: { list: vi.fn(() => of([testSupplier()])) } },
        { provide: ImportService, useValue: importer },
        { provide: InboxService, useValue: inbox },
        { provide: ConfigService, useValue: config },
      ],
    });
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(InvoiceList);
    fixture.detectChanges();
    list = fixture.componentInstance;
  });

  afterEach(() => vi.restoreAllMocks());

  it('lists the current year, latest first, ledgers kept apart', () => {
    expect(list.years[0]).toBe(new Date().getFullYear());
    expect(list.years.at(-1)).toBe(2024);
    expect(list.suppliers()).toHaveLength(1);

    expect(list.displayedInvoices().map(i => i.id)).toEqual([4, 1, 2, 3, 5]);
    expect(list.sortIndicator()).toBe(' ▼');

    list.toggleSort();
    expect(list.displayedInvoices().map(i => i.id)).toEqual([5, 3, 2, 1, 4]);
    expect(list.sortIndicator()).toBe(' ▲');
  });

  it('filters by type and series, and the totals follow', () => {
    expect(list.totals()).toEqual({ incVat: 36, exVat: 400, vat: 84 });

    list.typeFilter.set('SALE');
    expect(list.displayedInvoices().map(i => i.id)).toEqual([2]);

    list.typeFilter.set(null);
    list.seriesFilter.set('EXPENSE');
    expect(list.displayedInvoices().map(i => i.id)).toEqual([4]);
    expect(list.totals().incVat).toBe(5);
  });

  it('reloads another year', () => {
    list.onYearChange(2025);
    expect(invoices['list']).toHaveBeenLastCalledWith(2025);
  });

  it('searches only with criteria, then resets', () => {
    list.keyword = '   ';
    list.search();
    expect(invoices['search']).not.toHaveBeenCalled();

    list.keyword = ' fibre ';
    list.supplierId = 1;
    list.amountMin = 0;
    list.amountMax = 100;
    list.dateFrom = '2026-01-01';
    list.dateTo = '2026-12-31';
    list.category = 'TELECOM';
    list.search();
    expect(invoices['search']).toHaveBeenCalledWith({
      keyword: 'fibre',
      supplierId: 1,
      amountMin: 0,
      amountMax: 100,
      dateFrom: '2026-01-01',
      dateTo: '2026-12-31',
      category: 'TELECOM',
    });
    expect(list.searchActive).toBe(true);
    expect(list.invoices()).toHaveLength(1);

    list.resetSearch();
    expect(list.searchActive).toBe(false);
    expect(list.keyword).toBe('');
    expect(list.invoices()).toHaveLength(5);
  });

  it('opens a row in the editor', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    list.openInvoice(rows[0]);
    expect(navigate).toHaveBeenCalledWith(['/invoices', 1, 'edit']);
    expect(list.formatNumber(rows[0])).toBe('001');
  });

  it('deletes after confirmation and refreshes what is shown', () => {
    confirmSpy.mockReturnValueOnce(false);
    list.deleteInvoice(rows[0]);
    expect(invoices['delete']).not.toHaveBeenCalled();

    list.deleteInvoice(rows[0]);
    expect(invoices['list']).toHaveBeenCalledTimes(2);

    list.keyword = 'x';
    list.search();
    list.deleteInvoice(rows[0]);
    expect(invoices['search']).toHaveBeenCalledTimes(2);

    invoices['delete'].mockReturnValue(throwError(() => new Error('x')));
    list.deleteInvoice(rows[0]);
    expect(alertSpy).toHaveBeenCalled();
  });

  it('imports an Excel file and reports failures', () => {
    list.onImportFile(fileEvent());
    expect(importer['importExcel']).not.toHaveBeenCalled();

    list.onImportFile(fileEvent(new File(['x'], 'f.xlsx')));
    expect(list.importResult()?.invoicesImported).toBe(2);
    expect(list.importing()).toBe(false);

    importer['importExcel'].mockReturnValue(throwError(() => new Error('x')));
    list.onImportFile(fileEvent(new File(['x'], 'f.xlsx')));
    expect(list.importing()).toBe(false);
    expect(alertSpy).toHaveBeenCalled();
  });

  it('scans the inbox and refreshes the error counter', () => {
    list.scanInbox();
    expect(list.scanResult()?.matched).toBe(1);
    expect(config['loadConfig']).toHaveBeenCalled();

    inbox['scan'].mockReturnValue(throwError(() => new Error('x')));
    list.scanInbox();
    expect(list.scanning()).toBe(false);
    expect(alertSpy).toHaveBeenCalled();
  });
});
