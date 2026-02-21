import { DateScope, InvoiceType } from './invoice.model';

export interface PeppolDocument {
  id: string;
  receivedAt: string;
  invoiceDate: string | null;
  invoiceDueDate: string | null;
  amount: number | null;
  senderName: string;
  senderVatNumber: string | null;
  currency: string | null;
  invoiceReference: string | null;
  isCreditNote: boolean;
  alreadyImported: boolean;
  matchedSupplierId: number | null;
  matchedSupplierName: string | null;
}

export interface PeppolImportRequest {
  falcoDocumentId: string;
  supplierId: number;
  year: number;
  type: InvoiceType;
  dateScope: DateScope;
  scopeDate: string | null;
  fileDetail: string | null;
  comment: string | null;
}
