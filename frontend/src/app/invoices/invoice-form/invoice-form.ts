import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { switchMap } from 'rxjs';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { Supplier } from '../../models/supplier.model';
import { InvoiceRequest, DateScope, InvoiceType } from '../../models/invoice.model';

@Component({
  selector: 'app-invoice-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './invoice-form.html',
  styleUrl: './invoice-form.scss'
})
export class InvoiceForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly invoiceService = inject(InvoiceService);
  private readonly supplierService = inject(SupplierService);

  form!: FormGroup;
  suppliers = signal<Supplier[]>([]);
  isEdit = false;
  invoiceId?: number;
  selectedFile: File | null = null;
  existingFilePath: string | null = null;

  readonly invoiceTypes: { value: InvoiceType; label: string }[] = [
    { value: 'PURCHASE', label: 'Achat' },
    { value: 'SALE', label: 'Vente' },
  ];

  readonly dateScopes: { value: DateScope; label: string }[] = [
    { value: 'NONE', label: 'Aucune' },
    { value: 'DAILY', label: 'Journalière' },
    { value: 'MONTHLY', label: 'Mensuelle' },
    { value: 'QUARTERLY', label: 'Trimestrielle' },
    { value: 'YEARLY', label: 'Annuelle' },
  ];

  ngOnInit(): void {
    this.form = this.fb.group({
      year: [new Date().getFullYear(), Validators.required],
      subNumber: [null],
      type: ['PURCHASE', Validators.required],
      supplierId: [null, Validators.required],
      amountIncVat: [null],
      amountExVat: [null],
      vatAmount: [null],
      receptionDate: [this.todayString(), Validators.required],
      paymentDate: [null],
      peppol: [false, Validators.required],
      comment: [null],
      dateScope: ['NONE', Validators.required],
      scopeDate: [null],
      fileDetail: [null],
    });

    this.supplierService.list().subscribe(s => this.suppliers.set(s));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.invoiceId = +id;
      this.invoiceService.get(this.invoiceId).subscribe(inv => {
        this.existingFilePath = inv.filePath;
        this.form.patchValue({
          year: inv.year,
          subNumber: inv.subNumber,
          type: inv.type,
          supplierId: inv.supplier.id,
          amountIncVat: inv.amountIncVat,
          amountExVat: inv.amountExVat,
          vatAmount: inv.vatAmount,
          receptionDate: inv.receptionDate,
          paymentDate: inv.paymentDate,
          peppol: inv.peppol,
          comment: inv.comment,
          dateScope: inv.dateScope,
          scopeDate: inv.scopeDate,
          fileDetail: inv.fileDetail,
        });
      });
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  save(): void {
    if (this.form.invalid) return;

    const req: InvoiceRequest = {
      ...this.form.value,
      supplierId: +this.form.value.supplierId,
      subNumber: this.form.value.subNumber ? +this.form.value.subNumber : null,
      amountIncVat: this.form.value.amountIncVat ? +this.form.value.amountIncVat : null,
      amountExVat: this.form.value.amountExVat ? +this.form.value.amountExVat : null,
      vatAmount: this.form.value.vatAmount ? +this.form.value.vatAmount : null,
      paymentDate: this.form.value.paymentDate || null,
      scopeDate: this.form.value.scopeDate || null,
      comment: this.form.value.comment || null,
      filePath: this.existingFilePath,
      fileDetail: this.form.value.fileDetail || null,
    };

    const op = this.isEdit
      ? this.invoiceService.update(this.invoiceId!, req)
      : this.invoiceService.create(req);

    if (this.selectedFile) {
      const file = this.selectedFile;
      op.pipe(
        switchMap(invoice => this.invoiceService.upload(invoice.id, file))
      ).subscribe(() => this.router.navigate(['/invoices']));
    } else {
      op.subscribe(() => this.router.navigate(['/invoices']));
    }
  }

  private todayString(): string {
    return new Date().toISOString().substring(0, 10);
  }
}
