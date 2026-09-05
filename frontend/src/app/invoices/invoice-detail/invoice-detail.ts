import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { dateScopeLabel, Invoice } from '../../models/invoice.model';
import { EXPENSE_CATEGORY_LABELS } from '../../models/supplier.model';
import { DetailModal } from '../../shared/detail-modal/detail-modal';

@Component({
  selector: 'app-invoice-detail',
  imports: [RouterLink, CurrencyPipe, DatePipe, DetailModal],
  templateUrl: './invoice-detail.html'
})
export class InvoiceDetail {
  readonly invoice = input.required<Invoice>();
  readonly closed = output<void>();

  readonly title = computed(() => {
    const inv = this.invoice();
    const num = String(inv.number).padStart(3, '0');
    return `${inv.type === 'PURCHASE' ? 'Achat' : 'Vente'} n° ${inv.subNumber ? `${num}.${inv.subNumber}` : num} — ${inv.year}`;
  });

  readonly categoryLabel = computed(() => {
    const category = this.invoice().supplier.category;
    return category ? EXPENSE_CATEGORY_LABELS[category] : '';
  });

  readonly scopeLabel = computed(() => dateScopeLabel(this.invoice().dateScope));
}
