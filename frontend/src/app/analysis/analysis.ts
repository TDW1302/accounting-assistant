import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalysisService } from '../services/analysis.service';
import {
  CategoryComparison,
  CategoryComparisonRow,
  SupplierChanges,
  SupplierInventory,
} from '../models/analysis.model';
import { EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS, ExpenseCategory } from '../models/supplier.model';

type AnalysisTab = 'categories' | 'suppliers' | 'inventory';

/**
 * Restitue les agregats calcules par le serveur. Aucun montant n'est recalcule
 * ici: l'ecart entre deux annees est la seule arithmetique, et elle porte sur
 * des totaux deja exacts.
 */
@Component({
  selector: 'app-analysis',
  imports: [CurrencyPipe, DatePipe, NgTemplateOutlet, PercentPipe, FormsModule],
  templateUrl: './analysis.html',
  styleUrl: './analysis.scss'
})
export class Analysis implements OnInit {
  private readonly analysisService = inject(AnalysisService);

  readonly categories = EXPENSE_CATEGORIES;
  readonly currentYear = new Date().getFullYear();
  /** Aujourd'hui, en date locale: toISOString() basculerait sur la veille la nuit. */
  private readonly today = this.isoDate(new Date());

  tab = signal<AnalysisTab>('categories');
  years: number[] = [];
  selectedYear = this.currentYear;
  /**
   * Coupe les deux annees au meme jour du calendrier. Sans effet sur une annee
   * close: seule l'annee en cours est incomplete.
   */
  comparablePeriod = true;
  inventoryCategory: ExpenseCategory | null = null;

  loading = signal(false);
  error = signal<string | null>(null);
  comparison = signal<CategoryComparison | null>(null);
  changes = signal<SupplierChanges | null>(null);
  inventory = signal<SupplierInventory | null>(null);

  /** Echelle commune des barres: la plus grosse categorie, toutes annees confondues. */
  readonly scale = computed(() => {
    const rows = this.comparison()?.rows ?? [];
    return Math.max(0, ...rows.flatMap(r => [r.current.amountIncVat, r.previous.amountIncVat]));
  });

  ngOnInit(): void {
    for (let y = this.currentYear; y >= 2024; y--) {
      this.years.push(y);
    }
    this.load();
  }

  get previousYear(): number {
    return this.selectedYear - 1;
  }

  get isCurrentYear(): boolean {
    return this.selectedYear === this.currentYear;
  }

  /** Borne envoyee au serveur, nulle pour une annee close ou si l'utilisateur l'a decochee. */
  get until(): string | null {
    return this.isCurrentYear && this.comparablePeriod ? this.today : null;
  }

  selectTab(tab: AnalysisTab): void {
    this.tab.set(tab);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const done = () => this.loading.set(false);
    const fail = () => {
      this.loading.set(false);
      this.error.set("Impossible de charger l'analyse.");
    };

    switch (this.tab()) {
      case 'categories':
        this.analysisService.compareCategories(this.selectedYear, this.previousYear, this.until)
          .subscribe({ next: r => { this.comparison.set(r); done(); }, error: fail });
        break;
      case 'suppliers':
        this.analysisService.compareSuppliers(this.selectedYear, this.previousYear, this.until)
          .subscribe({ next: r => { this.changes.set(r); done(); }, error: fail });
        break;
      case 'inventory':
        this.analysisService.inventory(this.selectedYear, this.inventoryCategory)
          .subscribe({ next: r => { this.inventory.set(r); done(); }, error: fail });
        break;
    }
  }

  categoryLabel(category: ExpenseCategory | null): string {
    return category ? EXPENSE_CATEGORY_LABELS[category] : 'Sans catégorie';
  }

  barWidth(amount: number): string {
    const max = this.scale();
    return max > 0 ? `${(amount / max) * 100}%` : '0';
  }

  delta(row: CategoryComparisonRow): number {
    return row.current.amountIncVat - row.previous.amountIncVat;
  }

  /** Nul quand l'annee precedente est a zero: une hausse depuis rien n'a pas de pourcentage. */
  deltaRatio(row: CategoryComparisonRow): number | null {
    const previous = row.previous.amountIncVat;
    return previous > 0 ? this.delta(row) / previous : null;
  }

  /** Le sens porte le signe: les montants affiches a cote restent positifs. */
  arrow(value: number): string {
    return value > 0 ? '▲' : value < 0 ? '▼' : '=';
  }

  abs(value: number): number {
    return Math.abs(value);
  }

  private isoDate(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }
}
