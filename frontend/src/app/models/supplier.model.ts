export interface Supplier {
  id: number;
  name: string;
  alias: string | null;
  enterpriseNumber: string | null;
}

export interface SupplierRequest {
  name: string;
  alias: string | null;
  enterpriseNumber: string | null;
}
