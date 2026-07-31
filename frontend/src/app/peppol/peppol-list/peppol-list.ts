import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PeppolService } from '../../services/peppol.service';
import { SupplierService } from '../../services/supplier.service';
import { PeppolDocument, PeppolImportRequest } from '../../models/peppol.model';
import { Supplier } from '../../models/supplier.model';
import { DateScope, InvoiceType } from '../../models/invoice.model';

@Component({
  selector: 'app-peppol-list',
  imports: [CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './peppol-list.html',
  styleUrl: './peppol-list.scss'
})
export class PeppolList implements OnInit {
  private readonly peppolService = inject(PeppolService);
  private readonly supplierService = inject(SupplierService);

  documents = signal<PeppolDocument[]>([]);
  suppliers = signal<Supplier[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  // Filters
  receivedAfter = '';
  receivedBefore = '';
  senderName = '';

  // Import form state
  expandedDocId: string | null = null;
  importSupplierId: number | null = null;
  importYear = new Date().getFullYear();
  importType: InvoiceType = 'PURCHASE';
  importDateScope: DateScope = 'MONTHLY';
  importScopeDate = '';
  importFileDetail = '';
  importComment = '';
  importing = false;
  importingSuppliers = false;

  ngOnInit(): void {
    this.supplierService.list().subscribe(data => this.suppliers.set(data));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: Record<string, string> = {};
    if (this.receivedAfter) filters['receivedAfter'] = this.receivedAfter;
    if (this.receivedBefore) filters['receivedBefore'] = this.receivedBefore;
    if (this.senderName.trim()) filters['senderName'] = this.senderName.trim();

    this.peppolService.listInbound(filters).subscribe({
      next: data => {
        this.documents.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Erreur lors de la récupération des documents Peppol.');
        this.loading.set(false);
      }
    });
  }

  resetFilters(): void {
    this.receivedAfter = '';
    this.receivedBefore = '';
    this.senderName = '';
    this.load();
  }

  toggleImport(doc: PeppolDocument): void {
    if (this.expandedDocId === doc.id) {
      this.expandedDocId = null;
      return;
    }
    this.expandedDocId = doc.id;
    this.importSupplierId = doc.matchedSupplierId;
    this.importYear = new Date().getFullYear();
    this.importType = 'PURCHASE';
    this.importDateScope = this.supplierDefaultScope(doc.matchedSupplierId) ?? 'MONTHLY';
    this.importScopeDate = '';
    this.importFileDetail = '';
    this.importComment = '';
  }

  /** A supplier with a default scope (DKV monthly, Vanbrada yearly...) pre-fills the field. */
  onImportSupplierChange(): void {
    const scope = this.supplierDefaultScope(this.importSupplierId);
    if (scope) {
      this.importDateScope = scope;
    }
  }

  private supplierDefaultScope(supplierId: number | null): DateScope | null {
    return this.suppliers().find(s => s.id === supplierId)?.defaultDateScope ?? null;
  }

  importSuppliers(): void {
    this.importingSuppliers = true;
    this.error.set(null);
    this.peppolService.importSuppliers().subscribe({
      next: (suppliers) => {
        this.importingSuppliers = false;
        this.supplierService.list().subscribe(data => this.suppliers.set(data));
        this.load();
      },
      error: () => {
        this.importingSuppliers = false;
        this.error.set('Erreur lors de l\'import des fournisseurs.');
      }
    });
  }

  confirmImport(doc: PeppolDocument): void {
    if (!this.importSupplierId) return;
    this.importing = true;

    const request: PeppolImportRequest = {
      falcoDocumentId: doc.id,
      supplierId: this.importSupplierId,
      year: this.importYear,
      type: this.importType,
      dateScope: this.importDateScope,
      scopeDate: this.importScopeDate || null,
      fileDetail: this.importFileDetail || null,
      comment: this.importComment || null,
      amountIncVat: doc.amount,
      receptionDate: doc.receivedAt
    };

    this.peppolService.importDocument(request).subscribe({
      next: () => {
        this.expandedDocId = null;
        this.importing = false;
        this.load();
      },
      error: () => {
        this.importing = false;
        this.error.set('Erreur lors de l\'import du document.');
      }
    });
  }
}
