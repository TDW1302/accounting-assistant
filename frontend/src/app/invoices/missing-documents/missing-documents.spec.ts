import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MissingDocuments } from './missing-documents';
import { InvoiceService } from '../../services/invoice.service';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import {
  apiError,
  fileEvent,
  testConflict,
  testInvoice,
  testLinkOption,
  testRecurring,
  testSupplier,
} from '../../../testing/fixtures';

describe('MissingDocuments', () => {
  let invoices: Record<string, ReturnType<typeof vi.fn>>;
  let recurring: Record<string, ReturnType<typeof vi.fn>>;
  let screen: MissingDocuments;
  let alertSpy: ReturnType<typeof vi.spyOn>;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  const rent = testInvoice({ id: 7, displayNumber: '012', supplier: testSupplier({ id: 1 }) });
  const other = testInvoice({ id: 8, supplier: testSupplier({ id: 2 }) });

  beforeEach(() => {
    invoices = {
      missingDocuments: vi.fn(() => of([rent, other])),
      upload: vi.fn(() => of(testInvoice({ generatedFileName: '012-Ondes.pdf' }))),
    };
    recurring = {
      list: vi.fn(() =>
        of([
          testRecurring({ supplier: testSupplier({ id: 1 }) }),
          testRecurring({ id: 2, active: false, supplier: testSupplier({ id: 2 }) }),
        ]),
      ),
      linkOptions: vi.fn(() => of([testLinkOption()])),
      attach: vi.fn(() => of(rent)),
      replace: vi.fn(() => of(rent)),
    };
    TestBed.configureTestingModule({
      imports: [MissingDocuments],
      providers: [
        provideRouter([]),
        { provide: InvoiceService, useValue: invoices },
        { provide: RecurringExpenseService, useValue: recurring },
      ],
    });
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(MissingDocuments);
    fixture.detectChanges();
    screen = fixture.componentInstance;
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads every year without Peppol, and reloads on demand', () => {
    expect(invoices['missingDocuments']).toHaveBeenCalledWith(null, false);
    expect(screen.invoices()).toHaveLength(2);
    expect(screen.years.at(-1)).toBe(2024);

    screen.selectedYear = 2026;
    screen.includePeppol = true;
    screen.load();
    expect(invoices['missingDocuments']).toHaveBeenLastCalledWith(2026, true);

    invoices['missingDocuments'].mockReturnValue(throwError(() => new Error('x')));
    screen.load();
    expect(screen.loading()).toBe(false);
    expect(alertSpy).toHaveBeenCalled();
  });

  it('offers linking only where an active model exists', () => {
    expect(screen.canLink(rent)).toBe(true);
    expect(screen.canLink(other)).toBe(false);
  });

  it('opens and closes the link panel with the suggested periods', () => {
    screen.toggleLinkPanel(rent);
    expect(screen.linkPanelFor()).toBe(7);
    expect(screen.linkOptions()).toHaveLength(1);
    expect(screen.chosenPeriod[1]).toBe('2026-02-01');
    expect(screen.linkLoading()).toBe(false);

    screen.toggleLinkPanel(rent);
    expect(screen.linkPanelFor()).toBeNull();
  });

  it('explains why the options could not be listed', () => {
    recurring['linkOptions'].mockReturnValue(throwError(() => apiError('Forbidden row')));
    screen.toggleLinkPanel(rent);
    expect(screen.linkError()).toBe('Forbidden row');

    screen.toggleLinkPanel(other);
    recurring['linkOptions'].mockReturnValue(throwError(() => apiError()));
    screen.toggleLinkPanel(rent);
    expect(screen.linkError()).toContain('Impossible de lister');
  });

  it('attaches a row, which then leaves the list', () => {
    screen.toggleLinkPanel(rent);
    screen.chosenPeriod[1] = '2026-01-01';

    screen.attachTo(rent, testLinkOption());

    expect(recurring['attach']).toHaveBeenCalledWith(1, { invoiceId: 7, periodStart: '2026-01-01' });
    expect(screen.invoices().map(i => i.id)).toEqual([8]);
    expect(screen.linkPanelFor()).toBeNull();
    expect(screen.linkBusy()).toBe(false);
  });

  it('shows the server refusal, or a fallback', () => {
    recurring['attach'].mockReturnValue(throwError(() => apiError('Period already covered')));
    screen.attachTo(rent, testLinkOption());
    expect(screen.linkError()).toBe('Period already covered');

    recurring['attach'].mockReturnValue(throwError(() => null));
    screen.attachTo(rent, testLinkOption());
    expect(screen.linkError()).toBe('Rattachement impossible.');
    expect(screen.invoices()).toHaveLength(2);
  });

  it('replaces only a replaceable instalment, after confirmation', () => {
    screen.replaceOn(rent, testLinkOption({ conflict: null }));
    screen.replaceOn(rent, testLinkOption({ conflict: testConflict({ replaceable: false }) }));
    confirmSpy.mockReturnValueOnce(false);
    screen.replaceOn(rent, testLinkOption({ conflict: testConflict() }));
    expect(recurring['replace']).not.toHaveBeenCalled();

    screen.replaceOn(rent, testLinkOption({ conflict: testConflict() }));
    expect(recurring['replace']).toHaveBeenCalledTimes(1);
    expect(screen.invoices().map(i => i.id)).toEqual([8]);

    recurring['replace'].mockReturnValue(throwError(() => ({})));
    screen.replaceOn(other, testLinkOption({ conflict: testConflict() }));
    expect(screen.linkError()).toBe('Remplacement impossible.');
  });

  it('uploads a document, which removes the row', () => {
    screen.onFileSelected(fileEvent(), rent);
    expect(invoices['upload']).not.toHaveBeenCalled();

    screen.onFileSelected(fileEvent(new File(['%PDF'], 'a.pdf')), rent);
    expect(screen.lastUploaded()).toBe('012-Ondes.pdf');
    expect(screen.uploadingId()).toBeNull();
    expect(screen.invoices().map(i => i.id)).toEqual([8]);

    invoices['upload'].mockReturnValue(throwError(() => new Error('x')));
    screen.onFileSelected(fileEvent(new File(['%PDF'], 'a.pdf')), other);
    expect(alertSpy).toHaveBeenCalled();
    expect(screen.uploadingId()).toBeNull();
  });

  it('opens a row in the editor', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    screen.openInvoice(rent);
    expect(navigate).toHaveBeenCalledWith(['/invoices', 7, 'edit']);
    expect(screen.formatNumber(rent)).toBe('012');
  });
});
