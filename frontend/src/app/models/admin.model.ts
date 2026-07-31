export interface AdminYearSummary {
  year: number;
  invoiceCount: number;
}

export interface AdminStats {
  supplierCount: number;
  years: AdminYearSummary[];
}

export interface SupplierDuplicateCandidate {
  id: number;
  name: string;
  alias: string | null;
  enterpriseNumber: string | null;
  category: string | null;
  invoiceCount: number;
  documentCount: number;
}

export interface SupplierDuplicatePair {
  reason: string;
  left: SupplierDuplicateCandidate;
  right: SupplierDuplicateCandidate;
}

export interface SupplierDuplicates {
  suppliers: number;
  pairs: SupplierDuplicatePair[];
  withoutDocument: string[];
}

export interface SupplierMergeResult {
  keptId: number;
  keptName: string;
  removedName: string;
  invoicesReassigned: number;
  fieldsFilled: string[];
}
