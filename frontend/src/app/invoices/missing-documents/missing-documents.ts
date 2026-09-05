import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InvoiceService } from '../../services/invoice.service';
import { AuthService } from '../../services/auth.service';
import { Invoice } from '../../models/invoice.model';

@Component({
  selector: 'app-missing-documents',
  imports: [RouterLink, CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './missing-documents.html',
  styleUrl: './missing-documents.scss'
})
export class MissingDocuments implements OnInit {
  private readonly invoiceService = inject(InvoiceService);
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  invoices = signal<Invoice[]>([]);
  loading = signal(false);
  uploadingId = signal<number | null>(null);
  lastUploaded = signal<string | null>(null);

  selectedYear: number | null = null;
  /** Les factures Peppol ont leur document chez Falco: exclues sauf demande explicite. */
  includePeppol = false;
  years: number[] = [];

  ngOnInit(): void {
    const current = new Date().getFullYear();
    for (let y = current; y >= 2024; y--) {
      this.years.push(y);
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.invoiceService.missingDocuments(this.selectedYear, this.includePeppol).subscribe({
      next: data => {
        this.invoices.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        alert('Erreur lors du chargement des factures sans document.');
      }
    });
  }

  /** Double-clic sur une ligne: meme destination que son bouton Modifier. */
  openInvoice(inv: Invoice): void {
    this.router.navigate(['/invoices', inv.id, 'edit']);
  }

  onFileSelected(event: Event, inv: Invoice): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.uploadingId.set(inv.id);
    this.lastUploaded.set(null);
    this.invoiceService.upload(inv.id, file).subscribe({
      next: uploaded => {
        this.uploadingId.set(null);
        this.lastUploaded.set(uploaded.generatedFileName);
        // The invoice now has a document: it leaves this list.
        this.invoices.update(list => list.filter(i => i.id !== inv.id));
      },
      error: () => {
        this.uploadingId.set(null);
        alert('Erreur lors de l\'ajout du document.');
      }
    });
  }

  formatNumber(inv: Invoice): string {
    const num = String(inv.number).padStart(3, '0');
    return inv.subNumber ? `${num}.${inv.subNumber}` : num;
  }
}
