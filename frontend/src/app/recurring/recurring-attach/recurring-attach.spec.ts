import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RecurringAttach } from './recurring-attach';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import {
  apiError,
  testAttachable,
  testConflict,
  testInvoice,
  testRecurring,
} from '../../../testing/fixtures';

describe('RecurringAttach', () => {
  let recurring: Record<string, ReturnType<typeof vi.fn>>;
  let screen: RecurringAttach;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  const free = testAttachable({ invoiceId: 7 });
  const alsoFree = testAttachable({ invoiceId: 8, suggestedPeriodStart: '2026-03-01' });
  const blocked = testAttachable({
    invoiceId: 9,
    suggestionAvailable: false,
    suggestionIssue: 'Periode deja couverte',
    conflict: testConflict(),
  });

  beforeEach(() => {
    recurring = {
      get: vi.fn(() => of(testRecurring())),
      entries: vi.fn(() => of([testInvoice({ id: 50 })])),
      attachable: vi.fn(() => of([free, alsoFree, blocked])),
      attach: vi.fn(() => of(testInvoice())),
      replace: vi.fn(() => of(testInvoice())),
      detach: vi.fn(() => of(testInvoice())),
      attachAll: vi.fn(() => of({ attached: [{ id: 7, displayNumber: '012' }], skipped: [] })),
    };
    TestBed.configureTestingModule({
      imports: [RecurringAttach],
      providers: [
        provideRouter([]),
        { provide: RecurringExpenseService, useValue: recurring },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } } },
      ],
    });
    confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(RecurringAttach);
    fixture.detectChanges();
    screen = fixture.componentInstance;
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads the model, its entries and candidates, with free ones pre-selected', () => {
    expect(screen.expenseId).toBe(1);
    expect(screen.expense()?.label).toBe('Loyer bureau');
    expect(screen.entries()).toHaveLength(1);
    expect(screen.chosenPeriod[9]).toBe('2026-02-01');
    expect(screen.batchable().map(c => c.invoiceId)).toEqual([7, 8]);
    expect(screen.isSelected(free)).toBe(true);
    expect(screen.isSelected(blocked)).toBe(false);
    expect(screen.selectedCount()).toBe(2);
    expect(screen.allSelected()).toBe(true);
    expect(screen.periodicityLabel('QUARTERLY')).toBe('Trimestrielle');
  });

  it('toggles one row or all of them', () => {
    screen.toggle(free);
    expect(screen.allSelected()).toBe(false);
    screen.toggle(free);
    expect(screen.allSelected()).toBe(true);

    screen.toggleAll();
    expect(screen.selectedCount()).toBe(0);
    screen.toggleAll();
    expect(screen.selectedCount()).toBe(2);
  });

  it('attaches the checked rows in one batch with their periods', () => {
    screen.toggle(alsoFree);
    screen.chosenPeriod[7] = '2026-01-01';

    screen.attachSelected();

    expect(recurring['attachAll']).toHaveBeenCalledWith(1, {
      attachments: [{ invoiceId: 7, periodStart: '2026-01-01' }],
    });
    expect(screen.batchResult()?.attached).toHaveLength(1);
    expect(screen.batchRunning()).toBe(false);
    expect(recurring['attachable']).toHaveBeenCalledTimes(2);
  });

  it('does nothing without selection, and reports a failed batch', () => {
    screen.toggleAll();
    screen.attachSelected();
    expect(recurring['attachAll']).not.toHaveBeenCalled();

    screen.toggleAll();
    recurring['attachAll'].mockReturnValue(throwError(() => apiError('Nope')));
    screen.attachSelected();
    expect(screen.error()).toBe('Nope');

    recurring['attachAll'].mockReturnValue(throwError(() => apiError()));
    screen.attachSelected();
    expect(screen.error()).toBe('Rattachement en lot impossible.');
  });

  it('attaches one row with its chosen period, unless none is set', () => {
    screen.attach(free);
    expect(recurring['attach']).toHaveBeenCalledWith(1, { invoiceId: 7, periodStart: '2026-02-01' });
    expect(screen.busyInvoiceId()).toBeNull();

    screen.chosenPeriod[7] = '';
    screen.attach(free);
    expect(recurring['attach']).toHaveBeenCalledTimes(1);

    screen.chosenPeriod[7] = '2026-02-01';
    recurring['attach'].mockReturnValue(throwError(() => apiError()));
    screen.attach(free);
    expect(screen.error()).toBe('Rattachement impossible.');
  });

  it('replaces only a replaceable instalment, after confirmation', () => {
    screen.replace(free);
    screen.replace(testAttachable({ conflict: testConflict({ replaceable: false }) }));
    confirmSpy.mockReturnValueOnce(false);
    screen.replace(blocked);
    expect(recurring['replace']).not.toHaveBeenCalled();

    screen.replace(blocked);
    expect(recurring['replace']).toHaveBeenCalledWith(1, { invoiceId: 9, periodStart: '2026-02-01' });

    recurring['replace'].mockReturnValue(throwError(() => apiError('Has a document')));
    screen.replace(blocked);
    expect(screen.error()).toBe('Has a document');
  });

  it('detaches a row after confirmation, keeping it in the ledger', () => {
    const entry = testInvoice({ id: 50 });
    confirmSpy.mockReturnValueOnce(false);
    screen.detach(entry);
    expect(recurring['detach']).not.toHaveBeenCalled();

    screen.detach(entry);
    expect(recurring['detach']).toHaveBeenCalledWith(50);
    expect(screen.busyInvoiceId()).toBeNull();

    recurring['detach'].mockReturnValue(throwError(() => apiError('Not linked')));
    screen.detach(entry);
    expect(screen.error()).toBe('Not linked');

    recurring['detach'].mockReturnValue(throwError(() => apiError()));
    screen.detach(entry);
    expect(screen.error()).toBe('Détachement impossible.');
  });
});
