import { Invoice, InvoiceExtractionResult } from '../app/models/invoice.model';
import { Supplier } from '../app/models/supplier.model';
import {
  AttachableInvoice,
  ConflictingEntry,
  RecurringExpense,
  RecurringLinkOption,
} from '../app/models/recurring-expense.model';
import { User } from '../app/models/user.model';

/**
 * Donnees de test partagees entre specs. Hors de src/app pour ne pas compter
 * dans la couverture, et sans describe pour ne pas dupliquer de tests a l'import.
 */
export const testUser = (overrides: Partial<User> = {}): User =>
  ({
    id: 1,
    username: 'alice',
    email: 'alice@test.local',
    role: 'USER',
    enabled: true,
    aiProvider: 'CLAUDE',
    ...overrides,
  }) as User;

export const testSupplier = (overrides: Partial<Supplier> = {}): Supplier => ({
  id: 1,
  name: 'Ondes',
  alias: 'Ondes',
  enterpriseNumber: null,
  category: 'TELECOM',
  defaultDateScope: null,
  defaultPeppol: false,
  ...overrides,
});

export const testInvoice = (overrides: Partial<Invoice> = {}): Invoice => ({
  id: 1,
  number: 1,
  subNumber: null,
  displayNumber: '001',
  series: 'INVOICE',
  year: 2026,
  type: 'PURCHASE',
  supplier: testSupplier(),
  amountIncVat: 121,
  amountExVat: 100,
  vatAmount: 21,
  receptionDate: '2026-03-10',
  paymentDate: null,
  peppol: false,
  comment: null,
  filePath: null,
  dateScope: 'NONE',
  scopeDate: null,
  fileDetail: null,
  generatedFileName: '001-Ondes.pdf',
  falcoDocumentId: null,
  recurringExpenseId: null,
  recurringExpenseLabel: null,
  ...overrides,
});

export const emptyExtraction = (
  overrides: Partial<InvoiceExtractionResult> = {},
): InvoiceExtractionResult => ({
  type: null,
  supplierId: null,
  supplierName: null,
  amountIncVat: null,
  amountExVat: null,
  vatAmount: null,
  receptionDate: null,
  paymentDate: null,
  dateScope: null,
  scopeDate: null,
  comment: null,
  suggestedCategory: null,
  ...overrides,
});

export const testRecurring = (overrides: Partial<RecurringExpense> = {}): RecurringExpense => ({
  id: 1,
  label: 'Loyer bureau',
  supplier: testSupplier(),
  amountIncVat: 800,
  amountExVat: null,
  vatAmount: null,
  periodicity: 'MONTHLY',
  startDate: '2026-01-05',
  endDate: null,
  paidOnDueDate: true,
  comment: null,
  fileDetail: null,
  active: true,
  generatedCount: 0,
  lastGeneratedPeriod: null,
  pendingCount: 0,
  deletable: true,
  ...overrides,
});

export const testConflict = (overrides: Partial<ConflictingEntry> = {}): ConflictingEntry => ({
  invoiceId: 50,
  displayNumber: 'D001',
  year: 2026,
  amountIncVat: 800,
  receptionDate: '2026-01-05',
  replaceable: true,
  notReplaceableReason: null,
  ...overrides,
});

export const testAttachable = (overrides: Partial<AttachableInvoice> = {}): AttachableInvoice => ({
  invoiceId: 7,
  displayNumber: '012',
  year: 2026,
  receptionDate: '2026-02-03',
  amountIncVat: 800,
  comment: null,
  suggestedPeriodStart: '2026-02-01',
  suggestedPeriodLabel: '02/2026',
  suggestionAvailable: true,
  suggestionIssue: null,
  conflict: null,
  ...overrides,
});

export const testLinkOption = (overrides: Partial<RecurringLinkOption> = {}): RecurringLinkOption => ({
  recurringExpenseId: 1,
  label: 'Loyer bureau',
  periodicity: 'MONTHLY',
  suggestedPeriodStart: '2026-02-01',
  suggestedPeriodLabel: '02/2026',
  available: true,
  issue: null,
  conflict: null,
  ...overrides,
});

/** Erreur HTTP telle que la rend le GlobalExceptionHandler du backend. */
export const apiError = (message?: string) => ({ error: message ? { error: message } : null });

/** Un evenement de selection de fichier, tel que le recoit un (change). */
export const fileEvent = (...files: File[]) => {
  const input = document.createElement('input');
  input.type = 'file';
  Object.defineProperty(input, 'files', { value: files, configurable: true });
  return { target: input } as unknown as Event;
};
