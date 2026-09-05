import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EXPENSE_CATEGORY_LABELS, Supplier } from '../../models/supplier.model';
import { dateScopeLabel } from '../../models/invoice.model';
import { DetailModal } from '../../shared/detail-modal/detail-modal';

@Component({
  selector: 'app-supplier-detail',
  imports: [RouterLink, DetailModal],
  templateUrl: './supplier-detail.html'
})
export class SupplierDetail {
  readonly supplier = input.required<Supplier>();
  readonly closed = output<void>();

  readonly categoryLabel = computed(() => {
    const category = this.supplier().category;
    return category ? EXPENSE_CATEGORY_LABELS[category] : '';
  });

  readonly scopeLabel = computed(() => dateScopeLabel(this.supplier().defaultDateScope));
}
