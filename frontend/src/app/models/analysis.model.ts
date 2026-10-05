import { ExpenseCategory } from './supplier.model';

export interface AnalysisTotal {
  amountIncVat: number;
  amountExVat: number;
  count: number;
}

/** `category` nul: fournisseurs sans catégorie, ou ligne de total. */
export interface CategoryComparisonRow {
  category: ExpenseCategory | null;
  current: AnalysisTotal;
  previous: AnalysisTotal;
}

export interface CategoryComparison {
  year: number;
  previousYear: number;
  until: string | null;
  rows: CategoryComparisonRow[];
  total: CategoryComparisonRow;
  missingAmountCount: number;
}

export interface SupplierSummary {
  supplierId: number;
  supplierName: string;
  category: ExpenseCategory | null;
  count: number;
  amountIncVat: number;
  amountExVat: number;
  firstDate: string;
  lastDate: string;
}

export interface SupplierChanges {
  year: number;
  previousYear: number;
  until: string | null;
  newSuppliers: SupplierSummary[];
  missingSuppliers: SupplierSummary[];
}

export interface SupplierInventory {
  year: number;
  category: ExpenseCategory | null;
  suppliers: SupplierSummary[];
  total: AnalysisTotal;
  missingAmountCount: number;
}
