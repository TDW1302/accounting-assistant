import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { switchMap } from 'rxjs';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS, ExpenseCategory, Supplier, SupplierRequest } from '../../models/supplier.model';
import { InvoiceRequest, DATE_SCOPES, InvoiceType } from '../../models/invoice.model';
import { DocumentScanner } from '../document-scanner/document-scanner';

@Component({
  selector: 'app-invoice-form',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, DocumentScanner],
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
  autoExtract = true;
  extracting = false;
  submitted = false;
  showScanner = false;
  scannerImageFile: File | null = null;
  linkToNumber: number | null = null;

  unmatchedSupplierName: string | null = null;
  newSupplierCategory: ExpenseCategory | null = null;
  creatingSupplier = false;

  readonly categories: { value: ExpenseCategory; label: string }[] =
    EXPENSE_CATEGORIES.map(value => ({ value, label: EXPENSE_CATEGORY_LABELS[value] }));

  readonly invoiceTypes: { value: InvoiceType; label: string }[] = [
    { value: 'PURCHASE', label: 'Achat' },
    { value: 'SALE', label: 'Vente' },
  ];

  readonly dateScopes = DATE_SCOPES;

  /** A supplier with a default scope (DKV monthly, Vanbrada yearly...) pre-fills the field. */
  onSupplierChange(): void {
    this.applySupplierDefaultScope(this.form.get('supplierId')?.value);
  }

  private applySupplierDefaultScope(supplierId: number | string | null): void {
    if (!supplierId) return;
    const supplier = this.suppliers().find(s => s.id === Number(supplierId));
    if (supplier?.defaultDateScope) {
      this.form.patchValue({ dateScope: supplier.defaultDateScope });
    }
  }

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

    // Sub-invoice mode: read query params
    const linkTo = this.route.snapshot.queryParamMap.get('linkTo');
    const yearParam = this.route.snapshot.queryParamMap.get('year');
    if (linkTo && yearParam) {
      this.linkToNumber = +linkTo;
      this.form.patchValue({ year: +yearParam });
      this.form.get('year')!.disable();
    }

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
    const file = input.files?.[0] ?? null;

    if (file && this.isImageFile(file)) {
      this.scannerImageFile = file;
      this.showScanner = true;
      // Reset input so the same file can be re-selected
      input.value = '';
      return;
    }

    this.selectedFile = file;
    this.triggerExtraction();
  }

  onDocumentScanned(blob: Blob): void {
    this.selectedFile = new File([blob], 'scanned-document.jpg', { type: 'image/jpeg' });
    this.showScanner = false;
    this.scannerImageFile = null;
    this.triggerExtraction();
  }

  onScannerCancelled(): void {
    this.showScanner = false;
    this.scannerImageFile = null;
  }

  private isImageFile(file: File): boolean {
    return file.type.startsWith('image/');
  }

  private triggerExtraction(): void {
    if (this.selectedFile && !this.isEdit && this.autoExtract) {
      this.extracting = true;
      this.unmatchedSupplierName = null;
      this.newSupplierCategory = null;
      this.invoiceService.extract(this.selectedFile).subscribe({
        next: (result) => {
          const patch: Record<string, unknown> = {};
          if (result.type) patch['type'] = result.type;
          if (result.supplierId) patch['supplierId'] = String(result.supplierId);
          if (result.amountIncVat != null) patch['amountIncVat'] = result.amountIncVat;
          if (result.amountExVat != null) patch['amountExVat'] = result.amountExVat;
          if (result.vatAmount != null) patch['vatAmount'] = result.vatAmount;
          if (result.receptionDate) patch['receptionDate'] = result.receptionDate;
          if (result.paymentDate) patch['paymentDate'] = result.paymentDate;
          if (result.dateScope) patch['dateScope'] = result.dateScope;
          if (result.scopeDate) patch['scopeDate'] = result.scopeDate;
          if (result.comment) patch['comment'] = result.comment;
          this.form.patchValue(patch);
          // The supplier's own rule beats whatever scope the AI guessed for this document.
          this.applySupplierDefaultScope(result.supplierId);

          if (!result.supplierId && result.supplierName) {
            this.unmatchedSupplierName = result.supplierName;
            this.newSupplierCategory = result.suggestedCategory;
          }

          this.extracting = false;
        },
        error: () => {
          this.extracting = false;
        },
      });
    }
  }

  createSupplierFromExtraction(): void {
    if (!this.unmatchedSupplierName) return;

    this.creatingSupplier = true;
    const req: SupplierRequest = {
      name: this.unmatchedSupplierName,
      alias: null,
      enterpriseNumber: null,
      category: this.newSupplierCategory,
      defaultDateScope: null,
    };

    this.supplierService.create(req).subscribe({
      next: (supplier) => {
        this.suppliers.update(list => [...list, supplier]);
        this.form.patchValue({ supplierId: String(supplier.id) });
        this.unmatchedSupplierName = null;
        this.newSupplierCategory = null;
        this.creatingSupplier = false;
      },
      error: () => {
        this.creatingSupplier = false;
        alert('Erreur lors de la création du fournisseur.');
      },
    });
  }

  dismissUnmatchedSupplier(): void {
    this.unmatchedSupplierName = null;
    this.newSupplierCategory = null;
  }

  save(): void {
    this.submitted = true;
    if (this.form.invalid) return;

    const raw = this.form.getRawValue();
    const req: InvoiceRequest = {
      ...raw,
      supplierId: +raw.supplierId,
      subNumber: raw.subNumber ? +raw.subNumber : null,
      amountIncVat: raw.amountIncVat != null ? +raw.amountIncVat : null,
      amountExVat: raw.amountExVat != null ? +raw.amountExVat : null,
      vatAmount: raw.vatAmount != null ? +raw.vatAmount : null,
      paymentDate: raw.paymentDate || null,
      scopeDate: raw.scopeDate || null,
      comment: raw.comment || null,
      fileDetail: raw.fileDetail || null,
      linkToNumber: this.linkToNumber,
    };

    const op = this.isEdit
      ? this.invoiceService.update(this.invoiceId!, req)
      : this.invoiceService.create(req);

    if (this.selectedFile) {
      const file = this.selectedFile;
      op.pipe(
        switchMap(invoice => this.invoiceService.upload(invoice.id, file))
      ).subscribe({
        next: () => this.router.navigate(['/invoices']),
        error: () => alert('Erreur lors de l\'enregistrement de la facture.')
      });
    } else {
      op.subscribe({
        next: () => this.router.navigate(['/invoices']),
        error: () => alert('Erreur lors de l\'enregistrement de la facture.')
      });
    }
  }

  private todayString(): string {
    return new Date().toISOString().substring(0, 10);
  }
}
