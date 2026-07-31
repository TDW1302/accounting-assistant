import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { SupplierService } from '../../services/supplier.service';
import { EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS, ExpenseCategory, SupplierRequest } from '../../models/supplier.model';
import { DATE_SCOPES } from '../../models/invoice.model';

@Component({
  selector: 'app-supplier-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './supplier-form.html',
  styleUrl: './supplier-form.scss'
})
export class SupplierForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly supplierService = inject(SupplierService);

  form!: FormGroup;
  isEdit = false;
  supplierId?: number;

  readonly categories: { value: ExpenseCategory; label: string }[] =
    EXPENSE_CATEGORIES.map(value => ({ value, label: EXPENSE_CATEGORY_LABELS[value] }));

  readonly dateScopes = DATE_SCOPES;

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', Validators.required],
      alias: [null],
      enterpriseNumber: [null],
      category: [null],
      defaultDateScope: [null],
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.supplierId = +id;
      this.supplierService.get(this.supplierId).subscribe(s => {
        this.form.patchValue(s);
      });
    }
  }

  save(): void {
    if (this.form.invalid) return;

    const req: SupplierRequest = {
      name: this.form.value.name,
      alias: this.form.value.alias || null,
      enterpriseNumber: this.form.value.enterpriseNumber || null,
      category: this.form.value.category || null,
      defaultDateScope: this.form.value.defaultDateScope || null,
    };

    const op = this.isEdit
      ? this.supplierService.update(this.supplierId!, req)
      : this.supplierService.create(req);

    op.subscribe(() => this.router.navigate(['/suppliers']));
  }
}
