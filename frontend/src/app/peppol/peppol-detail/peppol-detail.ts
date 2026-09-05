import { Component, input, output } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { PeppolDocument } from '../../models/peppol.model';
import { DetailModal } from '../../shared/detail-modal/detail-modal';

@Component({
  selector: 'app-peppol-detail',
  imports: [CurrencyPipe, DatePipe, DetailModal],
  templateUrl: './peppol-detail.html'
})
export class PeppolDetail {
  readonly document = input.required<PeppolDocument>();
  readonly closed = output<void>();
}
