import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { Invoice } from '../../models/invoice.model';
import {
  AttachableInvoice,
  RecurringExpense,
  periodicityLabel,
} from '../../models/recurring-expense.model';

@Component({
  selector: 'app-recurring-attach',
  imports: [RouterLink, CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './recurring-attach.html',
  styleUrl: './recurring-attach.scss'
})
export class RecurringAttach implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly recurringService = inject(RecurringExpenseService);

  expenseId!: number;
  expense = signal<RecurringExpense | null>(null);
  entries = signal<Invoice[]>([]);
  candidates = signal<AttachableInvoice[]>([]);
  error = signal<string | null>(null);
  busyInvoiceId = signal<number | null>(null);

  /**
   * Période retenue par ligne. Pré-remplie avec celle que le serveur déduit de
   * la date de réception, corrigeable quand un loyer a été encodé en décalé.
   */
  readonly chosenPeriod: Record<number, string> = {};

  readonly periodicityLabel = periodicityLabel;

  ngOnInit(): void {
    this.expenseId = +this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  load(): void {
    this.recurringService.get(this.expenseId).subscribe(e => this.expense.set(e));
    this.recurringService.entries(this.expenseId).subscribe(e => this.entries.set(e));
    this.recurringService.attachable(this.expenseId).subscribe(list => {
      this.candidates.set(list);
      for (const candidate of list) {
        this.chosenPeriod[candidate.invoiceId] = candidate.suggestedPeriodStart;
      }
    });
  }

  attach(candidate: AttachableInvoice): void {
    const periodStart = this.chosenPeriod[candidate.invoiceId];
    if (!periodStart) return;

    this.error.set(null);
    this.busyInvoiceId.set(candidate.invoiceId);
    this.recurringService.attach(this.expenseId, {
      invoiceId: candidate.invoiceId,
      periodStart,
    }).subscribe({
      next: () => {
        this.busyInvoiceId.set(null);
        this.load();
      },
      error: (err) => {
        this.busyInvoiceId.set(null);
        this.error.set(err?.error?.message ?? 'Rattachement impossible.');
      },
    });
  }

  detach(invoice: Invoice): void {
    if (!confirm(`Détacher la ligne ${invoice.displayNumber} de ce modèle ?`)) return;

    this.error.set(null);
    this.busyInvoiceId.set(invoice.id);
    this.recurringService.detach(invoice.id).subscribe({
      next: () => {
        this.busyInvoiceId.set(null);
        this.load();
      },
      error: (err) => {
        this.busyInvoiceId.set(null);
        this.error.set(err?.error?.message ?? 'Détachement impossible.');
      },
    });
  }
}
