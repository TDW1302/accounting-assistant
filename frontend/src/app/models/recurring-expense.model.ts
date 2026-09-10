import { Supplier } from './supplier.model';

export type Periodicity = 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

export const PERIODICITIES: { value: Periodicity; label: string }[] = [
  { value: 'MONTHLY', label: 'Mensuelle' },
  { value: 'QUARTERLY', label: 'Trimestrielle' },
  { value: 'YEARLY', label: 'Annuelle' },
];

export function periodicityLabel(periodicity: Periodicity | null): string {
  return PERIODICITIES.find(p => p.value === periodicity)?.label ?? '';
}

/** Modèle d'une dépense contractuelle: ce qui est dû, à qui, et à quel rythme. */
export interface RecurringExpense {
  id: number;
  label: string;
  supplier: Supplier;
  amountIncVat: number | null;
  amountExVat: number | null;
  vatAmount: number | null;
  periodicity: Periodicity;
  startDate: string;
  endDate: string | null;
  paidOnDueDate: boolean;
  comment: string | null;
  fileDetail: string | null;
  active: boolean;
  generatedCount: number;
  lastGeneratedPeriod: string | null;
  pendingCount: number;
  deletable: boolean;
}

export interface RecurringExpenseRequest {
  label: string;
  supplierId: number;
  amountIncVat: number | null;
  amountExVat: number | null;
  vatAmount: number | null;
  periodicity: Periodicity;
  startDate: string;
  endDate: string | null;
  paidOnDueDate: boolean;
  comment: string | null;
  fileDetail: string | null;
  active: boolean;
}

/** Une échéance due mais pas encore inscrite au facturier. */
export interface RecurringOccurrence {
  recurringExpenseId: number;
  label: string;
  supplierName: string;
  periodStart: string;
  periodLabel: string;
  dueDate: string;
  year: number;
  amountIncVat: number | null;
}

export interface RecurringGenerationRequest {
  occurrences: { recurringExpenseId: number; periodStart: string }[];
}

export interface RecurringGenerationResponse {
  created: { id: number; displayNumber: string; year: number }[];
  skipped: string[];
}
