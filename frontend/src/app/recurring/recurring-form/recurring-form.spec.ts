import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RecurringForm } from './recurring-form';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { SupplierService } from '../../services/supplier.service';
import { apiError, testRecurring, testSupplier } from '../../../testing/fixtures';

describe('RecurringForm', () => {
  let recurring: Record<string, ReturnType<typeof vi.fn>>;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setup(params: Record<string, string> = {}, generatedCount = 0) {
    recurring = {
      get: vi.fn(() => of(testRecurring({ id: 3, generatedCount, endDate: '2026-12-31' }))),
      create: vi.fn(() => of(testRecurring())),
      update: vi.fn(() => of(testRecurring())),
    };
    TestBed.configureTestingModule({
      imports: [RecurringForm],
      providers: [
        provideRouter([]),
        { provide: RecurringExpenseService, useValue: recurring },
        { provide: SupplierService, useValue: { list: vi.fn(() => of([testSupplier()])) } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params) } } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(RecurringForm);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('starts a new monthly model on the first of the month', () => {
    const form = setup();
    const now = new Date();
    const first = new Date(Date.UTC(now.getFullYear(), now.getMonth(), 1)).toISOString().substring(0, 10);

    expect(form.form.get('startDate')!.value).toBe(first);
    expect(form.form.get('periodicity')!.value).toBe('MONTHLY');
    expect(form.suppliers()).toHaveLength(1);
  });

  it('refuses to save without label and supplier', () => {
    const form = setup();
    form.save();
    expect(form.submitted).toBe(true);
    expect(recurring['create']).not.toHaveBeenCalled();
  });

  it('creates a model with normalised values', () => {
    const form = setup();
    form.form.patchValue({ label: 'Loyer', supplierId: '1', amountIncVat: '800', endDate: '', paidOnDueDate: false });

    form.save();

    expect(recurring['create']).toHaveBeenCalledWith(
      expect.objectContaining({
        label: 'Loyer',
        supplierId: 1,
        amountIncVat: 800,
        amountExVat: null,
        endDate: null,
        paidOnDueDate: false,
        comment: null,
        active: true,
      }),
    );
    expect(navigate).toHaveBeenCalledWith(['/recurring']);
  });

  it('edits a model without entries freely', () => {
    const form = setup({ id: '3' });

    expect(form.isEdit).toBe(true);
    expect(form.scheduleLocked).toBe(false);
    expect(form.form.get('periodicity')!.enabled).toBe(true);

    form.save();
    expect(recurring['update']).toHaveBeenCalledWith(3, expect.objectContaining({ endDate: '2026-12-31' }));
  });

  it('locks the schedule of a model that already has entries', () => {
    const form = setup({ id: '3' }, 2);

    expect(form.scheduleLocked).toBe(true);
    expect(form.form.get('periodicity')!.disabled).toBe(true);
    expect(form.form.get('startDate')!.disabled).toBe(true);
  });

  it('shows the server refusal, or a fallback', () => {
    const form = setup({ id: '3' });
    recurring['update'].mockReturnValue(throwError(() => apiError('Cannot change the periodicity')));
    form.save();
    expect(form.error()).toBe('Cannot change the periodicity');
    expect(form.saving).toBe(false);

    recurring['update'].mockReturnValue(throwError(() => apiError()));
    form.save();
    expect(form.error()).toBe("Erreur lors de l'enregistrement.");
  });
});
