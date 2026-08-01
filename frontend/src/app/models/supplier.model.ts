import type { DateScope } from './invoice.model';

export type ExpenseCategory =
  | 'RESTAURANT'
  | 'TRANSPORT'
  | 'PARKING'
  | 'CARBURANT'
  | 'CONSOMMABLE'
  | 'INFORMATIQUE'
  | 'TELECOM'
  | 'ABONNEMENT'
  | 'ASSURANCE'
  | 'LOYER'
  | 'HONORAIRES'
  | 'MARKETING'
  | 'AUTRE';

export const EXPENSE_CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  RESTAURANT: 'Restaurant',
  TRANSPORT: 'Transport',
  PARKING: 'Parking',
  CARBURANT: 'Carburant',
  CONSOMMABLE: 'Consommable',
  INFORMATIQUE: 'Informatique',
  TELECOM: 'Télécom',
  ABONNEMENT: 'Abonnement',
  ASSURANCE: 'Assurance',
  LOYER: 'Loyer',
  HONORAIRES: 'Honoraires',
  MARKETING: 'Marketing',
  AUTRE: 'Autre',
};

export const EXPENSE_CATEGORIES: ExpenseCategory[] = Object.keys(EXPENSE_CATEGORY_LABELS) as ExpenseCategory[];

export interface Supplier {
  id: number;
  name: string;
  alias: string | null;
  enterpriseNumber: string | null;
  category: ExpenseCategory | null;
  /** Pre-fills the date scope of new invoices for this supplier. */
  defaultDateScope: DateScope | null;
  /** Pre-fills the Peppol flag of new invoices for this supplier. */
  defaultPeppol: boolean;
}

export interface SupplierRequest {
  name: string;
  alias: string | null;
  enterpriseNumber: string | null;
  category: ExpenseCategory | null;
  defaultDateScope: DateScope | null;
  defaultPeppol: boolean;
}
