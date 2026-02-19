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
}
