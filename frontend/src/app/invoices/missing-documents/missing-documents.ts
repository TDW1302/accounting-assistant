import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { InvoiceService } from '../../services/invoice.service';
import { RecurringExpenseService } from '../../services/recurring-expense.service';
import { AuthService } from '../../services/auth.service';
import { Invoice } from '../../models/invoice.model';
import { RecurringExpense, RecurringLinkOption } from '../../models/recurring-expense.model';

@Component({
  selector: 'app-missing-documents',
  imports: [RouterLink, CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './missing-documents.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './missing-documents.scss'
})
export class MissingDocuments implements OnInit {
  private readonly invoiceService = inject(InvoiceService);
  private readonly recurringService = inject(RecurringExpenseService);
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  invoices = signal<Invoice[]>([]);
  loading = signal(false);
  uploadingId = signal<number | null>(null);
  lastUploaded = signal<string | null>(null);

  /**
   * Fournisseurs couverts par au moins un modele actif. Sert a n'offrir le
   * rattachement que la ou il peut aboutir: le reste du temps, le bouton
   * promettrait une action que le serveur refuserait.
   */
  private suppliersWithModel = signal<Set<number>>(new Set());

  /** Facture dont le panneau de rattachement est ouvert. */
  linkPanelFor = signal<number | null>(null);
  linkOptions = signal<RecurringLinkOption[]>([]);
  linkLoading = signal(false);
  linkError = signal<string | null>(null);
  linkBusy = signal(false);
  /** Periode retenue par modele, corrigeable avant de rattacher. */
  readonly chosenPeriod: Record<number, string> = {};

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

    this.recurringService.list().subscribe(models => this.suppliersWithModel.set(
      new Set(models.filter((m: RecurringExpense) => m.active).map(m => m.supplier.id))));
  }

  /** Un rattachement n'a de sens que si un modele actif porte ce fournisseur. */
  canLink(inv: Invoice): boolean {
    return this.suppliersWithModel().has(inv.supplier.id);
  }

  toggleLinkPanel(inv: Invoice): void {
    if (this.linkPanelFor() === inv.id) {
      this.linkPanelFor.set(null);
      return;
    }

    this.linkPanelFor.set(inv.id);
    this.linkOptions.set([]);
    this.linkError.set(null);
    this.linkLoading.set(true);
    this.recurringService.linkOptions(inv.id).subscribe({
      next: options => {
        this.linkOptions.set(options);
        for (const option of options) {
          this.chosenPeriod[option.recurringExpenseId] = option.suggestedPeriodStart;
        }
        this.linkLoading.set(false);
      },
      error: (err) => {
        this.linkLoading.set(false);
        this.linkError.set(err?.error?.error
          ?? 'Impossible de lister les dépenses récurrentes pour cette facture.');
      },
    });
  }

  attachTo(inv: Invoice, option: RecurringLinkOption): void {
    this.runLink(inv, () => this.recurringService.attach(option.recurringExpenseId, {
      invoiceId: inv.id,
      periodStart: this.chosenPeriod[option.recurringExpenseId],
    }), 'Rattachement impossible.');
  }

  /** Supprime l'echeance engendree qui occupe la periode, puis rattache celle-ci. */
  replaceOn(inv: Invoice, option: RecurringLinkOption): void {
    const conflict = option.conflict;
    if (!conflict?.replaceable) return;
    if (!confirm(
      `Supprimer l'échéance ${conflict.displayNumber} engendrée par « ${option.label} », `
      + `et rattacher ${inv.displayNumber} à sa place ?`)) {
      return;
    }

    this.runLink(inv, () => this.recurringService.replace(option.recurringExpenseId, {
      invoiceId: inv.id,
      periodStart: this.chosenPeriod[option.recurringExpenseId],
    }), 'Remplacement impossible.');
  }

  private runLink(inv: Invoice, call: () => Observable<Invoice>, fallback: string): void {
    this.linkError.set(null);
    this.linkBusy.set(true);
    call().subscribe({
      next: () => {
        this.linkBusy.set(false);
        this.linkPanelFor.set(null);
        // La facture porte desormais une periode: elle quitte cette liste.
        this.invoices.update(list => list.filter(i => i.id !== inv.id));
      },
      error: (err) => {
        this.linkBusy.set(false);
        this.linkError.set(err?.error?.error ?? fallback);
      },
    });
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

  /** Le serveur met deja le numero en forme: le refaire ici le ferait diverger. */
  formatNumber(inv: Invoice): string {
    return inv.displayNumber;
  }
}
