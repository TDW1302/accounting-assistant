import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AdminStats } from '../../models/admin.model';

const CONFIRM_WORD = 'SUPPRIMER';

@Component({
  selector: 'app-danger-zone',
  imports: [FormsModule],
  templateUrl: './danger-zone.html',
  styleUrl: './danger-zone.scss'
})
export class DangerZone implements OnInit {
  private readonly adminService = inject(AdminService);

  readonly confirmWord = CONFIRM_WORD;

  stats = signal<AdminStats | null>(null);
  selectedYear = signal<number | null>(null);
  invoiceConfirmText = signal('');
  supplierConfirmText = signal('');
  busy = signal(false);
  error = signal('');

  ngOnInit() {
    this.load();
  }

  load() {
    this.adminService.getStats().subscribe(stats => {
      this.stats.set(stats);
      if (stats.years.length > 0 && this.selectedYear() === null) {
        this.selectedYear.set(stats.years[0].year);
      }
    });
  }

  deleteInvoicesForYear() {
    const year = this.selectedYear();
    if (year === null || this.invoiceConfirmText() !== this.confirmWord) {
      return;
    }
    this.error.set('');
    this.busy.set(true);
    this.adminService.deleteInvoicesByYear(year).subscribe({
      next: () => {
        this.busy.set(false);
        this.invoiceConfirmText.set('');
        this.selectedYear.set(null);
        this.load();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(err?.error?.error ?? 'Erreur lors de la suppression des factures.');
      }
    });
  }

  deleteAllSuppliers() {
    if (this.supplierConfirmText() !== this.confirmWord) {
      return;
    }
    this.error.set('');
    this.busy.set(true);
    this.adminService.deleteAllSuppliers().subscribe({
      next: () => {
        this.busy.set(false);
        this.supplierConfirmText.set('');
        this.load();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(err?.error?.error ?? 'Erreur lors de la suppression des fournisseurs.');
      }
    });
  }
}
