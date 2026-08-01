import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { AuthService } from '../../services/auth.service';
import { ImportService, ExcelImportResponse } from '../../services/import.service';
import { InboxService, InboxScanResult } from '../../services/inbox.service';
import { ConfigService } from '../../services/config.service';
import { Invoice, InvoiceType } from '../../models/invoice.model';
import { EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS, ExpenseCategory, Supplier } from '../../models/supplier.model';

@Component({
  selector: 'app-invoice-list',
  imports: [RouterLink, CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './invoice-list.html',
  styleUrl: './invoice-list.scss'
})
export class InvoiceList implements OnInit {
  private readonly invoiceService = inject(InvoiceService);
  private readonly supplierService = inject(SupplierService);
  readonly authService = inject(AuthService);
  private readonly importService = inject(ImportService);
  private readonly inboxService = inject(InboxService);
  private readonly configService = inject(ConfigService);

  invoices = signal<Invoice[]>([]);
  selectedYear = signal(new Date().getFullYear());
  years: number[] = [];
  suppliers = signal<Supplier[]>([]);
  importResult = signal<ExcelImportResponse | null>(null);
  importing = signal(false);
  scanResult = signal<InboxScanResult | null>(null);
  scanning = signal(false);

  /** Les dernieres factures d'abord: c'est sur elles qu'on travaille. */
  sortAsc = signal(false);
  typeFilter = signal<InvoiceType | null>(null);

  displayedInvoices = computed(() => {
    const type = this.typeFilter();
    const direction = this.sortAsc() ? 1 : -1;
    return this.invoices()
      .filter(inv => type === null || inv.type === type)
      .sort((a, b) => direction * this.compareByNumber(a, b));
  });

  /** Totaux des lignes affichees: ils suivent donc le filtre de type. */
  totals = computed(() => {
    const sum = (pick: (inv: Invoice) => number | null) =>
      this.displayedInvoices().reduce((acc, inv) => acc + (pick(inv) ?? 0), 0);
    return {
      incVat: sum(inv => inv.amountIncVat),
      exVat: sum(inv => inv.amountExVat),
      vat: sum(inv => inv.vatAmount),
    };
  });

  toggleSort(): void {
    this.sortAsc.update(asc => !asc);
  }

  sortIndicator(): string {
    return this.sortAsc() ? ' ▲' : ' ▼';
  }

  /** L'annee prime: une recherche multi-annees resterait sinon melangee. */
  private compareByNumber(a: Invoice, b: Invoice): number {
    if (a.year !== b.year) return a.year - b.year;
    if (a.number !== b.number) return a.number - b.number;
    return (a.subNumber ?? 0) - (b.subNumber ?? 0);
  }

  searchActive = false;
  keyword = '';
  supplierId: number | null = null;
  amountMin: number | null = null;
  amountMax: number | null = null;
  dateFrom = '';
  dateTo = '';
  category: ExpenseCategory | null = null;

  readonly categories: { value: ExpenseCategory; label: string }[] =
    EXPENSE_CATEGORIES.map(value => ({ value, label: EXPENSE_CATEGORY_LABELS[value] }));

  ngOnInit(): void {
    const current = new Date().getFullYear();
    for (let y = current; y >= 2024; y--) {
      this.years.push(y);
    }
    this.supplierService.list().subscribe(data => this.suppliers.set(data));
    this.load();
  }

  onYearChange(year: number): void {
    this.selectedYear.set(year);
    this.load();
  }

  load(): void {
    this.invoiceService.list(this.selectedYear()).subscribe(data => this.invoices.set(data));
  }

  search(): void {
    const params: Record<string, any> = {};
    if (this.keyword.trim()) params['keyword'] = this.keyword.trim();
    if (this.supplierId) params['supplierId'] = this.supplierId;
    if (this.amountMin !== null && this.amountMin !== undefined) params['amountMin'] = this.amountMin;
    if (this.amountMax !== null && this.amountMax !== undefined) params['amountMax'] = this.amountMax;
    if (this.dateFrom) params['dateFrom'] = this.dateFrom;
    if (this.dateTo) params['dateTo'] = this.dateTo;
    if (this.category) params['category'] = this.category;

    if (Object.keys(params).length === 0) {
      return;
    }

    this.searchActive = true;
    this.invoiceService.search(params).subscribe(data => this.invoices.set(data));
  }

  resetSearch(): void {
    this.searchActive = false;
    this.keyword = '';
    this.supplierId = null;
    this.amountMin = null;
    this.amountMax = null;
    this.dateFrom = '';
    this.dateTo = '';
    this.category = null;
    this.load();
  }

  deleteInvoice(inv: Invoice): void {
    if (confirm(`Supprimer la facture #${inv.number} ?`)) {
      this.invoiceService.delete(inv.id).subscribe({
        next: () => {
          if (this.searchActive) {
            this.search();
          } else {
            this.load();
          }
        },
        error: () => alert('Erreur lors de la suppression de la facture.')
      });
    }
  }

  formatNumber(inv: Invoice): string {
    const num = String(inv.number).padStart(3, '0');
    return inv.subNumber ? `${num}.${inv.subNumber}` : num;
  }

  onImportFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.importing.set(true);
    this.importResult.set(null);
    this.importService.importExcel(file).subscribe({
      next: (result) => {
        this.importResult.set(result);
        this.importing.set(false);
        this.load();
        this.supplierService.list().subscribe(data => this.suppliers.set(data));
      },
      error: () => {
        this.importing.set(false);
        alert('Erreur lors de l\'import Excel.');
      }
    });
    input.value = '';
  }

  scanInbox(): void {
    this.scanning.set(true);
    this.scanResult.set(null);
    this.inboxService.scan().subscribe({
      next: (result) => {
        this.scanResult.set(result);
        this.scanning.set(false);
        this.load();
        this.configService.loadConfig();
      },
      error: () => {
        this.scanning.set(false);
        alert('Erreur lors du scan de la boîte de dépôt.');
      }
    });
  }
}
