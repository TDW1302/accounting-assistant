import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PeppolDocument, PeppolImportRequest } from '../models/peppol.model';
import { Invoice } from '../models/invoice.model';
import { Supplier } from '../models/supplier.model';

@Injectable({ providedIn: 'root' })
export class PeppolService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/peppol';

  listInbound(filters?: { receivedAfter?: string; receivedBefore?: string; senderName?: string }): Observable<PeppolDocument[]> {
    let params = new HttpParams();
    if (filters) {
      if (filters.receivedAfter) params = params.set('receivedAfter', filters.receivedAfter);
      if (filters.receivedBefore) params = params.set('receivedBefore', filters.receivedBefore);
      if (filters.senderName) params = params.set('senderName', filters.senderName);
    }
    return this.http.get<PeppolDocument[]>(`${this.url}/inbound`, { params });
  }

  importDocument(request: PeppolImportRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/import`, request);
  }

  importSuppliers(): Observable<Supplier[]> {
    return this.http.post<Supplier[]>(`${this.url}/import-suppliers`, {});
  }
}
