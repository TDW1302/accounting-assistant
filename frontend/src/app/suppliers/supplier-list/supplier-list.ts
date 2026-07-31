import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SupplierService } from '../../services/supplier.service';
import { EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS, ExpenseCategory, Supplier } from '../../models/supplier.model';

export type SupplierSortColumn = 'name' | 'alias' | 'category';

@Component({
  selector: 'app-supplier-list',
  imports: [RouterLink, FormsModule],
  templateUrl: './supplier-list.html',
  styleUrl: './supplier-list.scss'
})
export class SupplierList implements OnInit {
  private readonly supplierService = inject(SupplierService);
  suppliers = signal<Supplier[]>([]);

  readonly categories: { value: ExpenseCategory; label: string }[] =
    EXPENSE_CATEGORIES.map(value => ({ value, label: EXPENSE_CATEGORY_LABELS[value] }));
  categoryLabel(category: ExpenseCategory | null): string {
    return category ? EXPENSE_CATEGORY_LABELS[category] : '';
  }

  categoryFilter: ExpenseCategory | null = null;

  sortColumn = signal<SupplierSortColumn>('name');
  sortAsc = signal(true);

  sortedSuppliers = computed(() => {
    const column = this.sortColumn();
    const direction = this.sortAsc() ? 1 : -1;
    return [...this.suppliers()].sort((a, b) => {
      const compared = this.compare(this.sortValue(a, column), this.sortValue(b, column));
      return compared !== 0 ? compared * direction : this.compare(a.name, b.name);
    });
  });

  sortBy(column: SupplierSortColumn): void {
    if (this.sortColumn() === column) {
      this.sortAsc.update(asc => !asc);
    } else {
      this.sortColumn.set(column);
      this.sortAsc.set(true);
    }
  }

  sortIndicator(column: SupplierSortColumn): string {
    if (this.sortColumn() !== column) return '';
    return this.sortAsc() ? ' ▲' : ' ▼';
  }

  private sortValue(supplier: Supplier, column: SupplierSortColumn): string {
    switch (column) {
      case 'alias': return supplier.alias ?? '';
      case 'category': return this.categoryLabel(supplier.category);
      default: return supplier.name;
    }
  }

  /** Empty values always last, whatever the direction. */
  private compare(a: string, b: string): number {
    if (!a) return b ? 1 : 0;
    if (!b) return -1;
    return a.localeCompare(b, 'fr-BE', { sensitivity: 'base', numeric: true });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.supplierService.list(this.categoryFilter).subscribe(data => this.suppliers.set(data));
  }

  deleteSupplier(s: Supplier): void {
    if (confirm(`Supprimer le fournisseur "${s.name}" ?`)) {
      this.supplierService.delete(s.id).subscribe({
        next: () => this.load(),
        error: () => alert('Erreur lors de la suppression du fournisseur.')
      });
    }
  }
}
