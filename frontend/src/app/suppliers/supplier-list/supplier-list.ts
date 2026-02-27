import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SupplierService } from '../../services/supplier.service';
import { Supplier } from '../../models/supplier.model';

@Component({
  selector: 'app-supplier-list',
  imports: [RouterLink],
  templateUrl: './supplier-list.html',
  styleUrl: './supplier-list.scss'
})
export class SupplierList implements OnInit {
  private readonly supplierService = inject(SupplierService);
  suppliers = signal<Supplier[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.supplierService.list().subscribe(data => this.suppliers.set(data));
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
