import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { AuthService } from '../../services/auth.service';
import {
  RecurringExpense,
  RecurringGenerationResponse,
  RecurringOccurrence,
  periodicityLabel,
} from '../../models/recurring-expense.model';

@Component({
  selector: 'app-recurring-list',
  imports: [RouterLink, CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './recurring-list.html',
  styleUrl: './recurring-list.scss'
})
export class RecurringList implements OnInit {
  private readonly recurringService = inject(RecurringExpenseService);
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);

  expenses = signal<RecurringExpense[]>([]);
  occurrences = signal<RecurringOccurrence[]>([]);
  generating = signal(false);
  result = signal<RecurringGenerationResponse | null>(null);

  /**
   * Jusqu'à quelle date les échéances sont considérées dues. Par défaut
   * aujourd'hui; l'avancer permet d'inscrire le loyer du mois prochain avant
   * qu'il ne tombe.
   */
  upTo = this.todayString();

  /** Clés des échéances cochées: un modèle peut en avoir plusieurs en attente. */
  private readonly selected = signal<Set<string>>(new Set());

  readonly periodicityLabel = periodicityLabel;

  selectedCount = computed(() => this.selected().size);

  allSelected = computed(() =>
    this.occurrences().length > 0 && this.selected().size === this.occurrences().length);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.recurringService.list().subscribe(data => this.expenses.set(data));
    this.loadDue();
  }

  loadDue(): void {
    this.recurringService.due(this.upTo).subscribe(data => {
      this.occurrences.set(data);
      // Tout est coché d'entrée: le cas courant est de tout inscrire, et
      // décocher une ligne est plus rapide que cocher les onze autres.
      this.selected.set(new Set(data.map(o => this.key(o))));
    });
  }

  key(occurrence: RecurringOccurrence): string {
    return `${occurrence.recurringExpenseId}|${occurrence.periodStart}`;
  }

  isSelected(occurrence: RecurringOccurrence): boolean {
    return this.selected().has(this.key(occurrence));
  }

  toggle(occurrence: RecurringOccurrence): void {
    this.selected.update(current => {
      const next = new Set(current);
      const key = this.key(occurrence);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  toggleAll(): void {
    const everything = this.allSelected();
    this.selected.set(everything ? new Set() : new Set(this.occurrences().map(o => this.key(o))));
  }

  selectedTotal = computed(() =>
    this.occurrences()
      .filter(o => this.isSelected(o))
      .reduce((acc, o) => acc + (o.amountIncVat ?? 0), 0));

  generate(): void {
    const chosen = this.occurrences().filter(o => this.isSelected(o));
    if (chosen.length === 0) return;

    this.generating.set(true);
    this.result.set(null);
    this.recurringService.generate({
      occurrences: chosen.map(o => ({
        recurringExpenseId: o.recurringExpenseId,
        periodStart: o.periodStart,
      })),
    }, this.upTo).subscribe({
      next: (response) => {
        this.result.set(response);
        this.generating.set(false);
        this.load();
      },
      error: () => {
        this.generating.set(false);
        alert('Erreur lors de la génération des échéances.');
      },
    });
  }

  openExpense(expense: RecurringExpense): void {
    this.router.navigate(['/recurring', expense.id, 'edit']);
  }

  deleteExpense(expense: RecurringExpense): void {
    if (!confirm(`Supprimer la dépense récurrente « ${expense.label} » ?`)) return;

    this.recurringService.delete(expense.id).subscribe({
      next: () => this.load(),
      error: (err) => alert(err?.error?.message
        ?? 'Suppression impossible: des échéances ont déjà été inscrites. '
           + 'Renseignez une date de fin ou désactivez le modèle.'),
    });
  }

  private todayString(): string {
    return new Date().toISOString().substring(0, 10);
  }
}
