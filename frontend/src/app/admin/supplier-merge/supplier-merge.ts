import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { SupplierService } from '../../services/supplier.service';
import { SupplierDuplicates, SupplierMergeResult } from '../../models/admin.model';
import { EXPENSE_CATEGORY_LABELS, ExpenseCategory, Supplier } from '../../models/supplier.model';

@Component({
  selector: 'app-supplier-merge',
  imports: [FormsModule],
  templateUrl: './supplier-merge.html',
  styleUrl: './supplier-merge.scss'
})
export class SupplierMerge implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly supplierService = inject(SupplierService);

  suppliers = signal<Supplier[]>([]);
  duplicates = signal<SupplierDuplicates | null>(null);
  lastMerge = signal<SupplierMergeResult | null>(null);
  loadingDuplicates = signal(false);
  busy = signal(false);
  error = signal('');

  keepId = signal<number | null>(null);
  removeId = signal<number | null>(null);

  keepSupplier = computed(() => this.supplierById(this.keepId()));
  removeSupplier = computed(() => this.supplierById(this.removeId()));

  canMerge = computed(() =>
    this.keepId() !== null && this.removeId() !== null && this.keepId() !== this.removeId());

  ngOnInit(): void {
    this.loadSuppliers();
    this.loadDuplicates();
  }

  categoryLabel(category: ExpenseCategory | null): string {
    return category ? EXPENSE_CATEGORY_LABELS[category] : '';
  }

  loadDuplicates(): void {
    this.loadingDuplicates.set(true);
    this.error.set('');
    this.adminService.findDuplicateSuppliers().subscribe({
      next: data => {
        this.duplicates.set(data);
        this.loadingDuplicates.set(false);
      },
      error: err => {
        this.loadingDuplicates.set(false);
        this.error.set(err?.error?.error ?? 'Erreur lors de la détection des doublons.');
      }
    });
  }

  /** Charge un couple detecte dans le comparateur, sans rien fusionner. */
  selectPair(keepId: number, removeId: number): void {
    this.keepId.set(keepId);
    this.removeId.set(removeId);
  }

  merge(): void {
    if (!this.canMerge() || this.busy()) return;

    const keep = this.keepSupplier();
    const remove = this.removeSupplier();
    const confirmed = confirm(
      `Fusionner "${remove?.name}" dans "${keep?.name}" ?\n\n`
      + `Les factures de "${remove?.name}" seront transférées vers "${keep?.name}", `
      + 'puis cette fiche sera supprimée. Cette opération est irréversible.'
    );
    if (!confirmed) return;

    this.busy.set(true);
    this.error.set('');
    this.adminService.mergeSuppliers(this.keepId()!, this.removeId()!).subscribe({
      next: result => {
        this.busy.set(false);
        this.lastMerge.set(result);
        this.keepId.set(null);
        this.removeId.set(null);
        this.loadSuppliers();
        this.loadDuplicates();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(err?.error?.error ?? 'Erreur lors de la fusion.');
      }
    });
  }

  private loadSuppliers(): void {
    this.supplierService.list().subscribe(data => this.suppliers.set(data));
  }

  private supplierById(id: number | null): Supplier | null {
    return id === null ? null : this.suppliers().find(s => s.id === id) ?? null;
  }
}
