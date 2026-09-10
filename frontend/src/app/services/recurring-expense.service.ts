import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
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
