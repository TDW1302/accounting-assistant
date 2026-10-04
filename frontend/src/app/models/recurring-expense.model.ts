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

/**
 * La ligne qui occupe déjà la période proposée. `replaceable` n'est vrai que
 * pour une échéance engendrée par le modèle : remplacer la supprime, et une
 * projection du modèle se refait d'un clic.
 */
export interface ConflictingEntry {
  invoiceId: number;
  displayNumber: string;
  year: number;
  amountIncVat: number | null;
  receptionDate: string;
  replaceable: boolean;
  notReplaceableReason: string | null;
}

/** Une ligne déjà au facturier qu'on peut rattacher à un modèle. */
export interface AttachableInvoice {
  invoiceId: number;
  displayNumber: string;
  year: number;
  receptionDate: string;
  amountIncVat: number | null;
  comment: string | null;
  suggestedPeriodStart: string;
  suggestedPeriodLabel: string;
  suggestionAvailable: boolean;
  suggestionIssue: string | null;
  conflict: ConflictingEntry | null;
}

export interface RecurringAttachRequest {
  invoiceId: number;
  periodStart: string;
}

export interface RecurringAttachBatchRequest {
  attachments: RecurringAttachRequest[];
}

export interface RecurringAttachBatchResponse {
  attached: { id: number; displayNumber: string }[];
  skipped: string[];
}

/**
 * Un modèle capable d'accueillir une facture donnée — le miroir de
 * `AttachableInvoice`, vu depuis la facture. La période proposée vient du
 * serveur : la ramener au début de sa période dépend du rythme, et réécrire
 * cette règle ici la ferait diverger.
 */
export interface RecurringLinkOption {
  recurringExpenseId: number;
  label: string;
  periodicity: Periodicity;
  suggestedPeriodStart: string;
  suggestedPeriodLabel: string;
  available: boolean;
  issue: string | null;
  conflict: ConflictingEntry | null;
}
