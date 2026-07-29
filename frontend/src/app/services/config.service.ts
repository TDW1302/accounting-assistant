import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface AppConfig {
  peppolEnabled: boolean;
  inboxErrorCount: number;
}

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly http = inject(HttpClient);

  readonly peppolEnabled = signal(false);
  readonly inboxErrorCount = signal(0);

  loadConfig() {
    this.http.get<AppConfig>('/api/config').subscribe({
      next: config => {
        this.peppolEnabled.set(config.peppolEnabled);
        this.inboxErrorCount.set(config.inboxErrorCount);
      },
      error: () => {
        this.peppolEnabled.set(false);
        this.inboxErrorCount.set(0);
      }
    });
  }
}
