import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RecurringList } from './recurring-list';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { RecurringOccurrence } from '../../models/recurring-expense.model';
import { apiError, testRecurring } from '../../../testing/fixtures';

describe('RecurringList', () => {
  let recurring: Record<string, ReturnType<typeof vi.fn>>;
  let screen: RecurringList;
  let alertSpy: ReturnType<typeof vi.spyOn>;
  let confirmSpy: ReturnType<typeof vi.spyOn>;

  const occurrence = (id: number, periodStart: string, amount: number | null): RecurringOccurrence => ({
    recurringExpenseId: id,
    label: 'Loyer',
    supplierName: 'Proprio',
    periodStart,
    periodLabel: periodStart,
    dueDate: periodStart,
    year: 2026,
    amountIncVat: amount,
  });
  const january = occurrence(1, '2026-01-01', 800);
  const february = occurrence(1, '2026-02-01', 800);
  const fees = occurrence(2, '2026-01-01', null);

  beforeEach(() => {
    recurring = {
      list: vi.fn(() => of([testRecurring()])),
      due: vi.fn(() => of([january, february, fees])),
      generate: vi.fn(() => of({ created: [{ id: 1, displayNumber: 'D001', year: 2026 }], skipped: [] })),
      delete: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({
      imports: [RecurringList],
      providers: [provideRouter([]), { provide: RecurringExpenseService, useValue: recurring }],
    });
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(RecurringList);
    fixture.detectChanges();
    screen = fixture.componentInstance;
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads models and due instalments up to today, all checked', () => {
    expect(recurring['due']).toHaveBeenCalledWith(new Date().toISOString().substring(0, 10));
    expect(screen.expenses()).toHaveLength(1);
    expect(screen.selectedCount()).toBe(3);
    expect(screen.allSelected()).toBe(true);
    expect(screen.selectedTotal()).toBe(1600);
    expect(screen.key(january)).toBe('1|2026-01-01');
    expect(screen.periodicityLabel('YEARLY')).toBe('Annuelle');
  });

  it('toggles instalments one by one or all together', () => {
    screen.toggle(february);
    expect(screen.isSelected(february)).toBe(false);
    expect(screen.selectedTotal()).toBe(800);
    expect(screen.allSelected()).toBe(false);

    screen.toggleAll();
    expect(screen.selectedCount()).toBe(3);
    screen.toggleAll();
    expect(screen.selectedCount()).toBe(0);
    screen.toggle(february);
    expect(screen.isSelected(february)).toBe(true);
  });

  it('generates the checked instalments up to the chosen date', () => {
    screen.toggle(fees);
    screen.upTo = '2026-02-28';

    screen.generate();

    expect(recurring['generate']).toHaveBeenCalledWith(
      {
        occurrences: [
          { recurringExpenseId: 1, periodStart: '2026-01-01' },
          { recurringExpenseId: 1, periodStart: '2026-02-01' },
        ],
      },
      '2026-02-28',
    );
    expect(screen.result()?.created).toHaveLength(1);
    expect(screen.generating()).toBe(false);
    expect(recurring['list']).toHaveBeenCalledTimes(2);
  });

  it('generates nothing without selection and reports failures', () => {
    screen.toggleAll();
    screen.generate();
    expect(recurring['generate']).not.toHaveBeenCalled();

    screen.toggleAll();
    recurring['generate'].mockReturnValue(throwError(() => new Error('x')));
    screen.generate();
    expect(screen.generating()).toBe(false);
    expect(alertSpy).toHaveBeenCalled();
  });

  it('an empty schedule is never "all selected"', () => {
    recurring['due'].mockReturnValue(of([]));
    screen.loadDue();
    expect(screen.allSelected()).toBe(false);
  });

  it('opens a model and deletes it after confirmation', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    screen.openExpense(testRecurring({ id: 4 }));
    expect(navigate).toHaveBeenCalledWith(['/recurring', 4, 'edit']);

    confirmSpy.mockReturnValueOnce(false);
    screen.deleteExpense(testRecurring());
    expect(recurring['delete']).not.toHaveBeenCalled();

    screen.deleteExpense(testRecurring());
    expect(recurring['delete']).toHaveBeenCalledWith(1);

    recurring['delete'].mockReturnValue(throwError(() => apiError('Has entries')));
    screen.deleteExpense(testRecurring());
    expect(alertSpy).toHaveBeenLastCalledWith('Has entries');

    recurring['delete'].mockReturnValue(throwError(() => apiError()));
    screen.deleteExpense(testRecurring());
    expect(alertSpy.mock.lastCall?.[0]).toContain('Suppression impossible');
  });
});
