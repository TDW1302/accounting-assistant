import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { Invoice } from '../../models/invoice.model';
import { Supplier } from '../../models/supplier.model';

@Component({
  selector: 'app-invoice-list',
  imports: [RouterLink, CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './invoice-list.html',
  styleUrl: './invoice-list.scss'
})
export class InvoiceList implements OnInit {
  private readonly invoiceService = inject(InvoiceService);
  private readonly supplierService = inject(SupplierService);

  invoices = signal<Invoice[]>([]);
  selectedYear = signal(new Date().getFullYear());
  years: number[] = [];
  suppliers = signal<Supplier[]>([]);

  searchActive = false;
  keyword = '';
  supplierId: number | null = null;
  amountMin: number | null = null;
  amountMax: number | null = null;
  dateFrom = '';
  dateTo = '';

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
    this.load();
  }

  deleteInvoice(inv: Invoice): void {
    if (confirm(`Supprimer la facture #${inv.number} ?`)) {
      this.invoiceService.delete(inv.id).subscribe(() => {
        if (this.searchActive) {
          this.search();
        } else {
          this.load();
        }
      });
    }
  }

  formatNumber(inv: Invoice): string {
    const num = String(inv.number).padStart(3, '0');
    return inv.subNumber ? `${num}.${inv.subNumber}` : num;
  }
}
