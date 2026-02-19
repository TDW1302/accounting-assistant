import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Supplier, SupplierRequest } from '../models/supplier.model';

@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/suppliers';

  list(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(this.url);
  }

  get(id: number): Observable<Supplier> {
    return this.http.get<Supplier>(`${this.url}/${id}`);
  }

  create(req: SupplierRequest): Observable<Supplier> {
    return this.http.post<Supplier>(this.url, req);
  }

  update(id: number, req: SupplierRequest): Observable<Supplier> {
    return this.http.put<Supplier>(`${this.url}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
