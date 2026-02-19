import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'invoices', pathMatch: 'full' },
  {
    path: 'invoices',
    loadComponent: () => import('./invoices/invoice-list/invoice-list').then(m => m.InvoiceList),
  },
  {
    path: 'invoices/new',
    loadComponent: () => import('./invoices/invoice-form/invoice-form').then(m => m.InvoiceForm),
  },
  {
    path: 'invoices/:id/edit',
    loadComponent: () => import('./invoices/invoice-form/invoice-form').then(m => m.InvoiceForm),
  },
  {
    path: 'suppliers',
    loadComponent: () => import('./suppliers/supplier-list/supplier-list').then(m => m.SupplierList),
  },
  {
    path: 'suppliers/new',
    loadComponent: () => import('./suppliers/supplier-form/supplier-form').then(m => m.SupplierForm),
  },
  {
    path: 'suppliers/:id/edit',
    loadComponent: () => import('./suppliers/supplier-form/supplier-form').then(m => m.SupplierForm),
  },
];
