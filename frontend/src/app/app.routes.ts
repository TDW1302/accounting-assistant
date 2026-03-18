import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { roleGuard } from './guards/role.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.Login) },
  { path: 'register', loadComponent: () => import('./auth/register/register').then(m => m.Register) },
  {
    path: 'change-password',
    loadComponent: () => import('./auth/change-password/change-password').then(m => m.ChangePassword),
    canActivate: [authGuard]
  },
  { path: '', redirectTo: 'invoices', pathMatch: 'full' },
  {
    path: 'invoices',
    loadComponent: () => import('./invoices/invoice-list/invoice-list').then(m => m.InvoiceList),
    canActivate: [authGuard]
  },
  {
    path: 'invoices/new',
    loadComponent: () => import('./invoices/invoice-form/invoice-form').then(m => m.InvoiceForm),
    canActivate: [authGuard, roleGuard(['ADMIN', 'USER'])]
  },
  {
    path: 'invoices/:id/edit',
    loadComponent: () => import('./invoices/invoice-form/invoice-form').then(m => m.InvoiceForm),
    canActivate: [authGuard, roleGuard(['ADMIN', 'USER'])]
  },
  {
    path: 'peppol',
    loadComponent: () => import('./peppol/peppol-list/peppol-list').then(m => m.PeppolList),
    canActivate: [authGuard]
  },
  {
    path: 'suppliers',
    loadComponent: () => import('./suppliers/supplier-list/supplier-list').then(m => m.SupplierList),
    canActivate: [authGuard]
  },
  {
    path: 'suppliers/new',
    loadComponent: () => import('./suppliers/supplier-form/supplier-form').then(m => m.SupplierForm),
    canActivate: [authGuard, roleGuard(['ADMIN', 'USER'])]
  },
  {
    path: 'suppliers/:id/edit',
    loadComponent: () => import('./suppliers/supplier-form/supplier-form').then(m => m.SupplierForm),
    canActivate: [authGuard, roleGuard(['ADMIN', 'USER'])]
  },
  {
    path: 'users',
    loadComponent: () => import('./users/user-list/user-list').then(m => m.UserList),
    canActivate: [authGuard, roleGuard(['ADMIN'])]
  },
  {
    path: 'users/new',
    loadComponent: () => import('./users/user-form/user-form').then(m => m.UserForm),
    canActivate: [authGuard, roleGuard(['ADMIN'])]
  },
  {
    path: 'users/:id/edit',
    loadComponent: () => import('./users/user-form/user-form').then(m => m.UserForm),
    canActivate: [authGuard, roleGuard(['ADMIN'])]
  },
  { path: '**', redirectTo: 'invoices' },
];
