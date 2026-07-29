import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { from, mergeMap, concatMap, of, catchError, EMPTY } from 'rxjs';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { Supplier } from '../../models/supplier.model';
import {
  BatchInvoiceItem,
  DateScope,
  InvoiceRequest,
  InvoiceType,
} from '../../models/invoice.model';

interface SupplierGroup {
  supplierId: number | null;
  supplierName: string;
  items: BatchInvoiceItem[];
}

@Component({
  selector: 'app-batch-upload',
  imports: [FormsModule, RouterLink],
  templateUrl: './batch-upload.html',
  styleUrl: './batch-upload.scss',
})
export class BatchUpload implements OnInit {
  private readonly router = inject(Router);
  private readonly invoiceService = inject(InvoiceService);
  private readonly supplierService = inject(SupplierService);

  files = signal<BatchInvoiceItem[]>([]);
  suppliers = signal<Supplier[]>([]);
  step = signal<'select' | 'review' | 'creating' | 'done'>('select');
  progress = signal<{ current: number; total: number }>({ current: 0, total: 0 });
  resultSummary = signal<{ created: number; errors: number }>({ created: 0, errors: 0 });

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

  groupedFiles = computed<SupplierGroup[]>(() => {
    const items = this.files();
    const suppliers = this.suppliers();
    const groups = new Map<number | null, BatchInvoiceItem[]>();

    for (const item of items) {
      const key = item.supplierId;
      if (!groups.has(key)) {
        groups.set(key, []);
      }
      groups.get(key)!.push(item);
    }

    return Array.from(groups.entries()).map(([supplierId, groupItems]) => {
      const supplier = supplierId != null ? suppliers.find(s => s.id === supplierId) : null;
      return {
        supplierId,
        supplierName: supplier?.name ?? '-- Non assigné --',
        items: groupItems,
      };
    });
  });

  extractingCount = computed(() => this.files().filter(f => f.status === 'extracting').length);
  allExtracted = computed(() => this.files().every(f => f.status === 'extracted' || f.status === 'error'));
  canCreate = computed(() => {
    const items = this.files();
    return items.length > 0 && items.every(f =>
      (f.status === 'extracted' || f.status === 'error') &&
      f.supplierId != null && f.type && f.receptionDate && f.year
    );
  });

  ngOnInit(): void {
    this.supplierService.list().subscribe(s => this.suppliers.set(s));
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selectedFiles = input.files;
    if (!selectedFiles || selectedFiles.length === 0) return;

    const today = new Date().toISOString().substring(0, 10);
    const year = new Date().getFullYear();

    const newItems: BatchInvoiceItem[] = Array.from(selectedFiles).map(file => ({
      file,
      status: 'pending' as const,
      extraction: null,
      year,
      type: 'PURCHASE' as InvoiceType,
      supplierId: null,
      amountIncVat: null,
      amountExVat: null,
      vatAmount: null,
      receptionDate: today,
      paymentDate: null,
      peppol: false,
      comment: null,
      dateScope: 'NONE' as DateScope,
      scopeDate: null,
      fileDetail: null,
      groupAsSubInvoices: false,
    }));

    this.files.set([...this.files(), ...newItems]);
    this.step.set('review');
    input.value = '';
    this.extractAll(newItems);
  }

  private extractAll(items: BatchInvoiceItem[]): void {
    const allFiles = this.files();

    // Mark items as extracting
    for (const item of items) {
      item.status = 'extracting';
    }
    this.files.set([...allFiles]);

    from(items).pipe(
      mergeMap(item =>
        this.invoiceService.extract(item.file).pipe(
          catchError(() => {
            item.status = 'error';
            this.files.update(f => [...f]);
            return EMPTY;
          }),
        ).pipe(
          mergeMap(result => {
            item.extraction = result;
            item.status = 'extracted';
            if (result.type) item.type = result.type;
            if (result.supplierId) item.supplierId = result.supplierId;
            if (result.amountIncVat != null) item.amountIncVat = result.amountIncVat;
            if (result.amountExVat != null) item.amountExVat = result.amountExVat;
            if (result.vatAmount != null) item.vatAmount = result.vatAmount;
            if (result.receptionDate) item.receptionDate = result.receptionDate;
            if (result.paymentDate) item.paymentDate = result.paymentDate;
            if (result.dateScope) item.dateScope = result.dateScope;
            if (result.scopeDate) item.scopeDate = result.scopeDate;
            if (result.comment) item.comment = result.comment;
            this.files.update(f => [...f]);
            return of(null);
          }),
        ),
        3, // concurrency limit
      ),
    ).subscribe();
  }

  toggleGroupSubInvoices(supplierId: number | null): void {
    this.files.update(items => {
      const groupItems = items.filter(i => i.supplierId === supplierId);
      const newValue = !groupItems[0]?.groupAsSubInvoices;
      for (const item of groupItems) {
        item.groupAsSubInvoices = newValue;
      }
      return [...items];
    });
  }

  removeFile(item: BatchInvoiceItem): void {
    this.files.update(items => items.filter(i => i !== item));
    if (this.files().length === 0) {
      this.step.set('select');
    }
  }

  supplierName(supplierId: number | null): string {
    if (supplierId == null) return '';
    return this.suppliers().find(s => s.id === supplierId)?.name ?? '';
  }

  createAll(): void {
    this.step.set('creating');
    const groups = this.groupedFiles();
    const allItems: { item: BatchInvoiceItem; linkToNumber: number | null }[] = [];

    // Build ordered list with linkToNumber info
    for (const group of groups) {
      if (group.items.length > 1 && group.items[0].groupAsSubInvoices) {
        // First item creates a normal invoice, subsequent ones link to it
        allItems.push({ item: group.items[0], linkToNumber: null });
        for (let i = 1; i < group.items.length; i++) {
          // linkToNumber will be set after first invoice is created
          allItems.push({ item: group.items[i], linkToNumber: -1 }); // placeholder
        }
      } else {
        for (const item of group.items) {
          allItems.push({ item, linkToNumber: null });
        }
      }
    }

    const total = allItems.length;
    this.progress.set({ current: 0, total });
    let created = 0;
    let errors = 0;

    // Process groups sequentially
    const groupEntries = groups.map(g => ({
      items: g.items,
      isSubGroup: g.items.length > 1 && g.items[0].groupAsSubInvoices,
    }));

    from(groupEntries).pipe(
      concatMap(group => {
        if (group.isSubGroup) {
          return this.createSubInvoiceGroup(group.items);
        } else {
          return from(group.items).pipe(
            concatMap(item => this.createSingleInvoice(item, null)),
          );
        }
      }),
    ).subscribe({
      next: (success) => {
        if (success) created++; else errors++;
        this.progress.update(p => ({ ...p, current: p.current + 1 }));
      },
      complete: () => {
        this.resultSummary.set({ created, errors });
        this.step.set('done');
      },
    });
  }

  private createSingleInvoice(item: BatchInvoiceItem, linkToNumber: number | null) {
    item.status = 'creating';
    this.files.update(f => [...f]);

    const req: InvoiceRequest = {
      subNumber: null,
      year: item.year,
      type: item.type,
      supplierId: item.supplierId!,
      amountIncVat: item.amountIncVat,
      amountExVat: item.amountExVat,
      vatAmount: item.vatAmount,
      receptionDate: item.receptionDate,
      paymentDate: item.paymentDate,
      peppol: item.peppol,
      comment: item.comment,
      filePath: null,
      dateScope: item.dateScope,
      scopeDate: item.scopeDate,
      fileDetail: item.fileDetail,
      linkToNumber,
    };

    return this.invoiceService.create(req).pipe(
      concatMap(invoice =>
        this.invoiceService.upload(invoice.id, item.file).pipe(
          catchError(() => of(invoice)), // upload failure is non-fatal
          mergeMap(() => {
            item.status = 'created';
            this.files.update(f => [...f]);
            return of({ success: true, number: invoice.number });
          }),
        ),
      ),
      catchError(() => {
        item.status = 'error';
        this.files.update(f => [...f]);
        return of({ success: false, number: 0 });
      }),
    );
  }

  private createSubInvoiceGroup(items: BatchInvoiceItem[]) {
    // First item: create normal invoice
    return this.createSingleInvoice(items[0], null).pipe(
      concatMap(result => {
        if (!result.success) {
          // If first fails, mark remaining as error too
          return from(items.slice(1)).pipe(
            mergeMap(item => {
              item.status = 'error';
              this.files.update(f => [...f]);
              return of(false);
            }),
          );
        }
        const linkToNumber = result.number;
        // Remaining items: create as sub-invoices
        if (items.length === 1) return EMPTY;
        return from(items.slice(1)).pipe(
          concatMap(item => this.createSingleInvoice(item, linkToNumber).pipe(
            mergeMap(r => of(r.success)),
          )),
        );
      }),
    );
  }

  addMoreFiles(): void {
    this.step.set('select');
  }
}
