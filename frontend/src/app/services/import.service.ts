import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ExcelImportResponse {
  suppliersCreated: number;
  invoicesImported: number;
  rowsSkipped: number;
  warnings: string[];
}

@Injectable({ providedIn: 'root' })
export class ImportService {
  private readonly http = inject(HttpClient);

  importExcel(file: File): Observable<ExcelImportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ExcelImportResponse>('/api/import/excel', formData);
  }
}
