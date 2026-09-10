import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Invoice } from '../models/invoice.model';
import {
  AttachableInvoice,
  RecurringAttachRequest,
  RecurringExpense,
  RecurringExpenseRequest,
  RecurringGenerationRequest,
  RecurringGenerationResponse,
  RecurringOccurrence,
} from '../models/recurring-expense.model';

@Injectable({ providedIn: 'root' })
export class RecurringExpenseService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/recurring-expenses';

  list(): Observable<RecurringExpense[]> {
    return this.http.get<RecurringExpense[]>(this.url);
  }

  get(id: number): Observable<RecurringExpense> {
    return this.http.get<RecurringExpense>(`${this.url}/${id}`);
  }

  create(req: RecurringExpenseRequest): Observable<RecurringExpense> {
    return this.http.post<RecurringExpense>(this.url, req);
  }

  update(id: number, req: RecurringExpenseRequest): Observable<RecurringExpense> {
    return this.http.put<RecurringExpense>(`${this.url}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Les lignes du facturier déjà rattachées à ce modèle. */
  entries(id: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.url}/${id}/entries`);
  }

  /** Lignes du même fournisseur, sans document, encore rattachables. */
  attachable(id: number): Observable<AttachableInvoice[]> {
    return this.http.get<AttachableInvoice[]>(`${this.url}/${id}/attachable`);
  }

  /** Rattache une ligne existante. Son numéro et son année ne changent pas. */
  attach(id: number, req: RecurringAttachRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/${id}/attach`, req);
  }

  detach(invoiceId: number): Observable<Invoice> {
    return this.http.delete<Invoice>(`${this.url}/entries/${invoiceId}`);
  }

  /** `upTo` permet de préparer l'avenir: le loyer de janvier avant le 1er. */
  due(upTo?: string | null): Observable<RecurringOccurrence[]> {
    let params = new HttpParams();
    if (upTo) params = params.set('upTo', upTo);
    return this.http.get<RecurringOccurrence[]>(`${this.url}/due`, { params });
  }

  generate(req: RecurringGenerationRequest, upTo?: string | null): Observable<RecurringGenerationResponse> {
    let params = new HttpParams();
    if (upTo) params = params.set('upTo', upTo);
    return this.http.post<RecurringGenerationResponse>(`${this.url}/generate`, req, { params });
  }
}
