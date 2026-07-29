export interface AdminYearSummary {
  year: number;
  invoiceCount: number;
}

export interface AdminStats {
  supplierCount: number;
  years: AdminYearSummary[];
}
