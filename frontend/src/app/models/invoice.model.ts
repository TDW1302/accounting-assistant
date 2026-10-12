import { ExpenseCategory, Supplier } from './supplier.model';

export type InvoiceType = 'PURCHASE' | 'SALE';

/**
 * Série de numérotation. INVOICE est le facturier documenté (001, 002…),
 * EXPENSE les dépenses contractuelles sans document (D001, D002…), qui ont
 * leur propre compteur annuel.
 */
export type InvoiceSeries = 'INVOICE' | 'EXPENSE';

export const INVOICE_SERIES: { value: InvoiceSeries; label: string }[] = [
  { value: 'INVOICE', label: 'Facture (document attendu)' },
  { value: 'EXPENSE', label: 'Dépense sans document' },
];
export type DateScope = 'DAILY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY' | 'NONE';

export const DATE_SCOPES: { value: DateScope; label: string }[] = [
  { value: 'NONE', label: 'Aucune' },
  { value: 'DAILY', label: 'Journalière' },
  { value: 'MONTHLY', label: 'Mensuelle' },
  { value: 'QUARTERLY', label: 'Trimestrielle' },
  { value: 'YEARLY', label: 'Annuelle' },
];

export function dateScopeLabel(scope: DateScope | null): string {
  return DATE_SCOPES.find(s => s.value === scope)?.label ?? '';
}

export interface Invoice {
  id: number;
  number: number;
  subNumber: number | null;
  /** Numéro mis en forme par le serveur: "001", "008.1", "D003". */
  displayNumber: string;
  series: InvoiceSeries;
  year: number;
  type: InvoiceType;
  supplier: Supplier;
  amountIncVat: number | null;
  amountExVat: number | null;
  vatAmount: number | null;
  receptionDate: string;
  paymentDate: string | null;
  peppol: boolean;
  comment: string | null;
  filePath: string | null;
  dateScope: DateScope;
  scopeDate: string | null;
  fileDetail: string | null;
  generatedFileName: string;
  falcoDocumentId: string | null;
  recurringExpenseId: number | null;
  recurringExpenseLabel: string | null;
}

export interface InvoiceExtractionResult {
  type: InvoiceType | null;
  supplierId: number | null;
  supplierName: string | null;
  amountIncVat: number | null;
  amountExVat: number | null;
  vatAmount: number | null;
  receptionDate: string | null;
  paymentDate: string | null;
  dateScope: DateScope | null;
  scopeDate: string | null;
  comment: string | null;
  suggestedCategory: ExpenseCategory | null;
}

export interface BatchInvoiceItem {
  file: File;
  status: 'pending' | 'extracting' | 'extracted' | 'matched' | 'error' | 'creating' | 'created';
  extraction: InvoiceExtractionResult | null;
  matchedInvoiceId: number | null;
  matchedNumber: number | null;
  matchedSubNumber: number | null;
  year: number;
  type: InvoiceType;
  supplierId: number | null;
  amountIncVat: number | null;
  amountExVat: number | null;
  vatAmount: number | null;
  receptionDate: string;
  paymentDate: string | null;
  peppol: boolean;
  comment: string | null;
  dateScope: DateScope;
  scopeDate: string | null;
  fileDetail: string | null;
  groupAsSubInvoices: boolean;
}

export interface InvoiceRequest {
  subNumber: number | null;
  series: InvoiceSeries;
  year: number;
  type: InvoiceType;
  supplierId: number;
  amountIncVat: number | null;
  amountExVat: number | null;
  vatAmount: number | null;
  receptionDate: string;
  paymentDate: string | null;
  peppol: boolean;
  comment: string | null;
  filePath: string | null;
  dateScope: DateScope;
  scopeDate: string | null;
  fileDetail: string | null;
  linkToNumber: number | null;
}
