import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface InboxScanResult {
  filesProcessed: number;
  matched: number;
  created: number;
  errors: number;
  errorFiles: string[];
}

@Injectable({ providedIn: 'root' })
export class InboxService {
  private readonly http = inject(HttpClient);

  scan(): Observable<InboxScanResult> {
    return this.http.post<InboxScanResult>('/api/inbox/scan', {});
  }
}
