import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SupplierService } from '../../services/supplier.service';
import { EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS, ExpenseCategory, Supplier } from '../../models/supplier.model';

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
