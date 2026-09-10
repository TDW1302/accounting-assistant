import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { SupplierService } from '../../services/supplier.service';
import { Supplier } from '../../models/supplier.model';
import { PERIODICITIES, RecurringExpenseRequest } from '../../models/recurring-expense.model';

@Component({
  selector: 'app-recurring-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './recurring-form.html',
  styleUrl: './recurring-form.scss'
})
export class RecurringForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recurringService = inject(RecurringExpenseService);
  private readonly supplierService = inject(SupplierService);

  form!: FormGroup;
  suppliers = signal<Supplier[]>([]);
  isEdit = false;
  expenseId?: number;
  submitted = false;
  saving = false;
  error = signal<string | null>(null);

  /**
   * Le rythme et la date de début identifient les échéances déjà inscrites:
   * les changer ferait glisser les périodes. Le serveur les refuse, le
   * formulaire les grise plutôt que de laisser l'erreur arriver au submit.
   */
  scheduleLocked = false;

  readonly periodicities = PERIODICITIES;

  ngOnInit(): void {
    this.form = this.fb.group({
      label: [null, Validators.required],
      supplierId: [null, Validators.required],
      amountIncVat: [null],
      amountExVat: [null],
      vatAmount: [null],
      periodicity: ['MONTHLY', Validators.required],
      startDate: [this.firstOfThisMonth(), Validators.required],
      endDate: [null],
      paidOnDueDate: [true],
      comment: [null],
      fileDetail: [null],
      active: [true],
    });

    this.supplierService.list().subscribe(s => this.suppliers.set(s));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.expenseId = +id;
      this.recurringService.get(this.expenseId).subscribe(expense => {
        this.scheduleLocked = expense.generatedCount > 0;
        this.form.patchValue({
          label: expense.label,
          supplierId: expense.supplier.id,
          amountIncVat: expense.amountIncVat,
          amountExVat: expense.amountExVat,
          vatAmount: expense.vatAmount,
          periodicity: expense.periodicity,
          startDate: expense.startDate,
          endDate: expense.endDate,
          paidOnDueDate: expense.paidOnDueDate,
          comment: expense.comment,
          fileDetail: expense.fileDetail,
          active: expense.active,
        });
        if (this.scheduleLocked) {
          this.form.get('periodicity')!.disable();
          this.form.get('startDate')!.disable();
        }
      });
    }
  }

  save(): void {
    this.submitted = true;
    this.error.set(null);
    if (this.form.invalid) return;

    const raw = this.form.getRawValue();
    const req: RecurringExpenseRequest = {
      label: raw.label,
      supplierId: +raw.supplierId,
      amountIncVat: raw.amountIncVat != null ? +raw.amountIncVat : null,
      amountExVat: raw.amountExVat != null ? +raw.amountExVat : null,
      vatAmount: raw.vatAmount != null ? +raw.vatAmount : null,
      periodicity: raw.periodicity,
      startDate: raw.startDate,
      endDate: raw.endDate || null,
      paidOnDueDate: !!raw.paidOnDueDate,
      comment: raw.comment || null,
      fileDetail: raw.fileDetail || null,
      active: !!raw.active,
    };

    this.saving = true;
    const op = this.isEdit
      ? this.recurringService.update(this.expenseId!, req)
      : this.recurringService.create(req);

    op.subscribe({
      next: () => this.router.navigate(['/recurring']),
      error: (err) => {
        this.saving = false;
        this.error.set(err?.error?.message ?? 'Erreur lors de l\'enregistrement.');
      },
    });
  }

  private firstOfThisMonth(): string {
    const now = new Date();
    return new Date(Date.UTC(now.getFullYear(), now.getMonth(), 1)).toISOString().substring(0, 10);
  }
}
