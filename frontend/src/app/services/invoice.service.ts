import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Invoice, InvoiceExtractionResult, InvoiceRequest } from '../models/invoice.model';
import { ExpenseCategory } from '../models/supplier.model';

export interface InvoiceSearchParams {
  year?: number;
  supplierId?: number;
  amountMin?: number;
  amountMax?: number;
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  category?: ExpenseCategory;
}

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/invoices';

  list(year: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.url, { params: { year } });
  }

  search(params: InvoiceSearchParams): Observable<Invoice[]> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return this.http.get<Invoice[]>(`${this.url}/search`, { params: httpParams });
  }

  get(id: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.url}/${id}`);
  }

  create(req: InvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(this.url, req);
  }

  update(id: number, req: InvoiceRequest): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.url}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  extract(file: File): Observable<InvoiceExtractionResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<InvoiceExtractionResult>(`${this.url}/extract`, formData);
  }

  upload(id: number, file: File): Observable<Invoice> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Invoice>(`${this.url}/${id}/upload`, formData);
  }
}
