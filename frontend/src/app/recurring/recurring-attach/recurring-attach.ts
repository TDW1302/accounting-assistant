import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { Invoice } from '../../models/invoice.model';
import {
  AttachableInvoice,
  RecurringAttachBatchResponse,
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
  batchRunning = signal(false);
  batchResult = signal<RecurringAttachBatchResponse | null>(null);

  /**
   * Période retenue par ligne. Pré-remplie avec celle que le serveur déduit de
   * la date de réception, corrigeable quand un loyer a été encodé en décalé.
   */
  readonly chosenPeriod: Record<number, string> = {};

  /** Lignes cochées pour le rattachement en lot. */
  private readonly selected = signal<Set<number>>(new Set());

  readonly periodicityLabel = periodicityLabel;

  /**
   * Seules les lignes sans conflit sont rattachables en lot. Une période déjà
   * occupée se règle une par une, avec « Remplacer »: supprimer des lignes du
   * facturier en série pour faire de la place n'a pas à passer par une case.
   */
  batchable = computed(() => this.candidates().filter(c => c.suggestionAvailable));

  selectedCount = computed(() => this.selected().size);

  allSelected = computed(() => {
    const eligible = this.batchable();
    return eligible.length > 0 && eligible.every(c => this.selected().has(c.invoiceId));
  });

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
      // Tout ce qui passe est coché d'entrée: reprendre un historique entier est
      // le cas courant, et décocher une ligne est plus rapide que cocher trente.
      this.selected.set(new Set(list.filter(c => c.suggestionAvailable).map(c => c.invoiceId)));
    });
  }

  isSelected(candidate: AttachableInvoice): boolean {
    return this.selected().has(candidate.invoiceId);
  }

  toggle(candidate: AttachableInvoice): void {
    this.selected.update(current => {
      const next = new Set(current);
      if (next.has(candidate.invoiceId)) {
        next.delete(candidate.invoiceId);
      } else {
        next.add(candidate.invoiceId);
      }
      return next;
    });
  }

  toggleAll(): void {
    const everything = this.allSelected();
    this.selected.set(everything ? new Set() : new Set(this.batchable().map(c => c.invoiceId)));
  }

  attachSelected(): void {
    const chosen = this.batchable().filter(c => this.isSelected(c));
    if (chosen.length === 0) return;

    this.error.set(null);
    this.batchResult.set(null);
    this.batchRunning.set(true);
    this.recurringService.attachAll(this.expenseId, {
      attachments: chosen.map(c => ({
        invoiceId: c.invoiceId,
        periodStart: this.chosenPeriod[c.invoiceId],
      })),
    }).subscribe({
      next: (response) => {
        this.batchResult.set(response);
        this.batchRunning.set(false);
        this.load();
      },
      error: (err) => {
        this.batchRunning.set(false);
        this.error.set(err?.error?.error ?? 'Rattachement en lot impossible.');
      },
    });
  }

  attach(candidate: AttachableInvoice): void {
    this.run(candidate, () => this.recurringService.attach(this.expenseId, {
      invoiceId: candidate.invoiceId,
      periodStart: this.chosenPeriod[candidate.invoiceId],
    }), 'Rattachement impossible.');
  }

  /** Supprime l'échéance engendrée qui occupe la période, puis rattache celle-ci. */
  replace(candidate: AttachableInvoice): void {
    const conflict = candidate.conflict;
    if (!conflict?.replaceable) return;
    if (!confirm(
      `Supprimer l'échéance ${conflict.displayNumber} engendrée par le modèle, `
      + `et rattacher ${candidate.displayNumber} à sa place ?`)) {
      return;
    }

    this.run(candidate, () => this.recurringService.replace(this.expenseId, {
      invoiceId: candidate.invoiceId,
      periodStart: this.chosenPeriod[candidate.invoiceId],
    }), 'Remplacement impossible.');
  }

  detach(invoice: Invoice): void {
    if (!confirm(
      `Détacher la ligne ${invoice.displayNumber} de ce modèle ?\n\n`
      + `La ligne reste au facturier avec son montant: détacher n'est pas supprimer.`)) {
      return;
    }

    this.error.set(null);
    this.busyInvoiceId.set(invoice.id);
    this.recurringService.detach(invoice.id).subscribe({
      next: () => {
        this.busyInvoiceId.set(null);
        this.load();
      },
      error: (err) => {
        this.busyInvoiceId.set(null);
        this.error.set(err?.error?.error ?? 'Détachement impossible.');
      },
    });
  }

  private run(candidate: AttachableInvoice, call: () => Observable<Invoice>,
               fallback: string): void {
    const periodStart = this.chosenPeriod[candidate.invoiceId];
    if (!periodStart) return;

    this.error.set(null);
    this.batchResult.set(null);
    this.busyInvoiceId.set(candidate.invoiceId);
    call().subscribe({
      next: () => {
        this.busyInvoiceId.set(null);
        this.load();
      },
      error: (err) => {
        this.busyInvoiceId.set(null);
        this.error.set(err?.error?.error ?? fallback);
      },
    });
  }
}
