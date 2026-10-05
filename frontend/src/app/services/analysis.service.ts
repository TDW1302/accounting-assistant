import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoryComparison, SupplierChanges, SupplierInventory } from '../models/analysis.model';
import { ExpenseCategory } from '../models/supplier.model';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/analysis';

  compareCategories(year: number, previousYear: number, until: string | null): Observable<CategoryComparison> {
    return this.http.get<CategoryComparison>(`${this.url}/categories`,
      { params: this.comparisonParams(year, previousYear, until) });
  }

  compareSuppliers(year: number, previousYear: number, until: string | null): Observable<SupplierChanges> {
    return this.http.get<SupplierChanges>(`${this.url}/suppliers/changes`,
      { params: this.comparisonParams(year, previousYear, until) });
  }

  inventory(year: number, category: ExpenseCategory | null): Observable<SupplierInventory> {
    const params: Record<string, string | number> = { year };
    if (category) params['category'] = category;
    return this.http.get<SupplierInventory>(`${this.url}/suppliers`, { params });
  }

  private comparisonParams(year: number, previousYear: number, until: string | null): Record<string, string | number> {
    const params: Record<string, string | number> = { year, previousYear };
    if (until) params['until'] = until;
    return params;
  }
}
