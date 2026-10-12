import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminService } from './admin.service';
import { ImportService } from './import.service';
import { InboxService } from './inbox.service';
import { InvoiceService } from './invoice.service';
import { PeppolService } from './peppol.service';
import { RecurringExpenseService } from './recurring-expense.service';
import { SupplierService } from './supplier.service';
import { UserService } from './user.service';
import { ConfigService } from './config.service';

/**
 * Les services HTTP ne portent pas de logique metier, mais leurs URL, verbes et
 * parametres sont le contrat avec le backend: une faute ici ne se voit qu'en
 * production.
 */
describe('HTTP services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectCall(method: string, url: string, body: object | null = null) {
    const req = http.expectOne(r => r.method === method && r.urlWithParams === url);
    req.flush(body);
    return req;
  }

  it('AdminService', () => {
    const service = TestBed.inject(AdminService);
    service.getStats().subscribe();
    expectCall('GET', '/api/admin/stats');
    service.deleteInvoicesByYear(2026).subscribe();
    expectCall('DELETE', '/api/admin/invoices?year=2026');
    service.deleteAllSuppliers().subscribe();
    expectCall('DELETE', '/api/admin/suppliers');
    service.findDuplicateSuppliers().subscribe();
    expectCall('GET', '/api/admin/suppliers/duplicates');
    service.mergeSuppliers(1, 2).subscribe();
    expectCall('POST', '/api/admin/suppliers/merge?keepId=1&removeId=2');
  });

  it('ImportService and InboxService', () => {
    const file = new File(['x'], 'facturier.xlsx');
    TestBed.inject(ImportService).importExcel(file).subscribe();
    const req = expectCall('POST', '/api/import/excel');
    expect((req.request.body as FormData).get('file')).toBe(file);

    TestBed.inject(InboxService).scan().subscribe();
    expectCall('POST', '/api/inbox/scan');
  });

  it('InvoiceService', () => {
    const service = TestBed.inject(InvoiceService);
    const file = new File(['x'], 'a.pdf');
    service.list(2026).subscribe();
    expectCall('GET', '/api/invoices?year=2026');
    service
      .search({ year: 2026, keyword: '', supplierId: undefined, amountMin: 0, category: 'TELECOM' })
      .subscribe();
    expectCall('GET', '/api/invoices/search?year=2026&amountMin=0&category=TELECOM');
    service.missingDocuments(2026, true).subscribe();
    expectCall('GET', '/api/invoices/missing-documents?includePeppol=true&year=2026');
    service.missingDocuments(null).subscribe();
    expectCall('GET', '/api/invoices/missing-documents?includePeppol=false');
    service.get(3).subscribe();
    expectCall('GET', '/api/invoices/3');
    service.create({} as never).subscribe();
    expectCall('POST', '/api/invoices');
    service.update(3, {} as never).subscribe();
    expectCall('PUT', '/api/invoices/3');
    service.delete(3).subscribe();
    expectCall('DELETE', '/api/invoices/3');
    service.extract(file).subscribe();
    expectCall('POST', '/api/invoices/extract');
    service.upload(3, file).subscribe();
    expectCall('POST', '/api/invoices/3/upload');
  });

  it('PeppolService', () => {
    const service = TestBed.inject(PeppolService);
    service.listInbound().subscribe();
    expectCall('GET', '/api/peppol/inbound');
    service
      .listInbound({ receivedAfter: '2026-01-01', receivedBefore: '2026-02-01', senderName: 'Od' })
      .subscribe();
    expectCall(
      'GET',
      '/api/peppol/inbound?receivedAfter=2026-01-01&receivedBefore=2026-02-01&senderName=Od',
    );
    service.listInbound({}).subscribe();
    expectCall('GET', '/api/peppol/inbound');
    service.importDocument({} as never).subscribe();
    expectCall('POST', '/api/peppol/import');
    service.importSuppliers().subscribe();
    expectCall('POST', '/api/peppol/import-suppliers');
  });

  it('RecurringExpenseService', () => {
    const service = TestBed.inject(RecurringExpenseService);
    service.list().subscribe();
    expectCall('GET', '/api/recurring-expenses');
    service.get(1).subscribe();
    expectCall('GET', '/api/recurring-expenses/1');
    service.create({} as never).subscribe();
    expectCall('POST', '/api/recurring-expenses');
    service.update(1, {} as never).subscribe();
    expectCall('PUT', '/api/recurring-expenses/1');
    service.delete(1).subscribe();
    expectCall('DELETE', '/api/recurring-expenses/1');
    service.entries(1).subscribe();
    expectCall('GET', '/api/recurring-expenses/1/entries');
    service.linkOptions(7).subscribe();
    expectCall('GET', '/api/recurring-expenses/options/7');
    service.attachable(1).subscribe();
    expectCall('GET', '/api/recurring-expenses/1/attachable');
    service.attach(1, { invoiceId: 7, periodStart: '2026-01-01' }).subscribe();
    expectCall('POST', '/api/recurring-expenses/1/attach');
    service.attachAll(1, { attachments: [] }).subscribe();
    expectCall('POST', '/api/recurring-expenses/1/attach-batch');
    service.replace(1, { invoiceId: 7, periodStart: '2026-01-01' }).subscribe();
    expectCall('POST', '/api/recurring-expenses/1/replace');
    service.detach(7).subscribe();
    expectCall('DELETE', '/api/recurring-expenses/entries/7');
    service.due().subscribe();
    expectCall('GET', '/api/recurring-expenses/due');
    service.due('2026-12-31').subscribe();
    expectCall('GET', '/api/recurring-expenses/due?upTo=2026-12-31');
    service.generate({ occurrences: [] }).subscribe();
    expectCall('POST', '/api/recurring-expenses/generate');
    service.generate({ occurrences: [] }, '2026-12-31').subscribe();
    expectCall('POST', '/api/recurring-expenses/generate?upTo=2026-12-31');
  });

  it('SupplierService and UserService', () => {
    const suppliers = TestBed.inject(SupplierService);
    suppliers.list().subscribe();
    expectCall('GET', '/api/suppliers');
    suppliers.list('TELECOM').subscribe();
    expectCall('GET', '/api/suppliers?category=TELECOM');
    suppliers.get(1).subscribe();
    expectCall('GET', '/api/suppliers/1');
    suppliers.create({} as never).subscribe();
    expectCall('POST', '/api/suppliers');
    suppliers.update(1, {} as never).subscribe();
    expectCall('PUT', '/api/suppliers/1');
    suppliers.delete(1).subscribe();
    expectCall('DELETE', '/api/suppliers/1');

    const users = TestBed.inject(UserService);
    users.list().subscribe();
    expectCall('GET', '/api/users');
    users.get(1).subscribe();
    expectCall('GET', '/api/users/1');
    users.create({} as never).subscribe();
    expectCall('POST', '/api/users');
    users.update(1, {} as never).subscribe();
    expectCall('PUT', '/api/users/1');
    users.delete(1).subscribe();
    expectCall('DELETE', '/api/users/1');
  });

  it('ConfigService loads the flags and falls back on failure', () => {
    const service = TestBed.inject(ConfigService);
    service.loadConfig();
    http.expectOne('/api/config').flush({ peppolEnabled: true, inboxErrorCount: 3 });
    expect(service.peppolEnabled()).toBe(true);
    expect(service.inboxErrorCount()).toBe(3);

    service.loadConfig();
    http.expectOne('/api/config').flush(null, { status: 500, statusText: 'Error' });
    expect(service.peppolEnabled()).toBe(false);
    expect(service.inboxErrorCount()).toBe(0);
  });
});
