import { Supplier } from './supplier.model';

export type InvoiceType = 'PURCHASE' | 'SALE';
export type DateScope = 'DAILY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY' | 'NONE';

export interface Invoice {
  id: number;
  number: number;
  subNumber: number | null;
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
