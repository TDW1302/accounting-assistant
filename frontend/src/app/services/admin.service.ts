import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminStats } from '../models/admin.model';

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
}
