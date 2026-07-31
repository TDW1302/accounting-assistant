import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminStats, SupplierDuplicates, SupplierMergeResult } from '../models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/admin';

  getStats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.url}/stats`);
  }

  deleteInvoicesByYear(year: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/invoices`, { params: { year } });
  }

  deleteAllSuppliers(): Observable<void> {
    return this.http.delete<void>(`${this.url}/suppliers`);
  }

  findDuplicateSuppliers(): Observable<SupplierDuplicates> {
    return this.http.get<SupplierDuplicates>(`${this.url}/suppliers/duplicates`);
  }

  mergeSuppliers(keepId: number, removeId: number): Observable<SupplierMergeResult> {
    return this.http.post<SupplierMergeResult>(`${this.url}/suppliers/merge`, null, {
      params: { keepId, removeId }
    });
  }
}
