import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BatchUpload } from './batch-upload';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import { BatchInvoiceItem } from '../../models/invoice.model';
import { emptyExtraction, fileEvent, testInvoice, testSupplier } from '../../../testing/fixtures';

describe('BatchUpload', () => {
  let invoices: Record<string, ReturnType<typeof vi.fn>>;
  let batch: BatchUpload;

  const pdf = (name: string) => new File(['%PDF'], name, { type: 'application/pdf' });
  const year = new Date().getFullYear();

  beforeEach(() => {
    invoices = {
      list: vi.fn(() =>
        of([
          testInvoice({ id: 10, number: 1, subNumber: null, supplier: testSupplier({ id: 2 }), type: 'SALE' }),
          testInvoice({ id: 11, number: 2, subNumber: 1, filePath: '/x/002.1.pdf' }),
        ]),
      ),
      extract: vi.fn((file: File) =>
        file.name === 'broken.pdf'
          ? throwError(() => new Error('AI down'))
          : of(
              emptyExtraction({
                type: 'PURCHASE',
                supplierId: 1,
                amountIncVat: 12.1,
                amountExVat: 10,
                vatAmount: 2.1,
                receptionDate: '2026-02-01',
                paymentDate: '2026-02-28',
                dateScope: 'DAILY',
                scopeDate: '2026-02-01',
                comment: 'Ref',
              }),
            ),
      ),
      create: vi.fn((req: { supplierId: number }) => of(testInvoice({ id: 50 + req.supplierId, number: 7 }))),
      upload: vi.fn(() => of(testInvoice())),
    };
    TestBed.configureTestingModule({
      imports: [BatchUpload],
      providers: [
        provideRouter([]),
        { provide: InvoiceService, useValue: invoices },
        {
          provide: SupplierService,
          useValue: {
            list: vi.fn(() =>
              of([
                testSupplier({ id: 1, name: 'Ondes', defaultDateScope: 'MONTHLY', defaultPeppol: true }),
                testSupplier({ id: 2, name: 'Acme Conseil' }),
              ]),
            ),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(BatchUpload);
    fixture.detectChanges();
    batch = fixture.componentInstance;
  });

  const item = (name: string) => batch.files().find(f => f.file.name === name)!;

  it('ignores an empty selection', () => {
    batch.onFilesSelected(fileEvent());
    expect(batch.step()).toBe('select');
    expect(invoices['list']).not.toHaveBeenCalled();
  });

  it('attaches files named after an invoice without document and extracts the others', () => {
    batch.onFilesSelected(fileEvent(pdf('001-Acme.pdf'), pdf('002.1-x.pdf'), pdf('scan.pdf'), pdf('broken.pdf')));

    expect(batch.step()).toBe('review');
    expect(invoices['list']).toHaveBeenCalledWith(year);

    const matched = item('001-Acme.pdf');
    expect(matched.status).toBe('matched');
    expect(matched.matchedInvoiceId).toBe(10);
    expect(matched.supplierId).toBe(2);
    expect(matched.type).toBe('SALE');

    // Deja pourvue d'un document: c'est un nouveau fichier a lire.
    const extracted = item('002.1-x.pdf');
    expect(extracted.status).toBe('extracted');
    expect(extracted.amountIncVat).toBe(12.1);
    expect(extracted.paymentDate).toBe('2026-02-28');
    // La regle du fournisseur l'emporte sur la portee devinee par l'IA.
    expect(extracted.dateScope).toBe('MONTHLY');
    expect(extracted.peppol).toBe(true);

    expect(item('broken.pdf').status).toBe('error');
    expect(batch.extractingCount()).toBe(0);
    expect(batch.allExtracted()).toBe(true);
    // Le fichier en erreur n'a pas de fournisseur: rien ne peut encore partir.
    expect(batch.canCreate()).toBe(false);

    const groups = batch.groupedFiles();
    expect(groups.map(g => g.supplierName)).toEqual(['Acme Conseil', 'Ondes', '-- Non assigné --']);
    expect(batch.supplierName(1)).toBe('Ondes');
    expect(batch.supplierName(99)).toBe('');
    expect(batch.supplierName(null)).toBe('');

    item('broken.pdf').supplierId = 1;
    batch.files.update(f => [...f]);
    expect(batch.canCreate()).toBe(true);
  });

  it('extracts everything when the year cannot be listed', () => {
    invoices['list'].mockReturnValue(throwError(() => new Error('x')));

    batch.onFilesSelected(fileEvent(pdf('001-Acme.pdf')));

    expect(item('001-Acme.pdf').status).toBe('extracted');
  });

  it('keeps the AI values a document does not provide', () => {
    invoices['extract'].mockReturnValue(of(emptyExtraction()));

    batch.onFilesSelected(fileEvent(pdf('scan.pdf')));

    const scanned = item('scan.pdf');
    expect(scanned.supplierId).toBeNull();
    expect(scanned.dateScope).toBe('NONE');
    expect(scanned.receptionDate).toBe(new Date().toISOString().substring(0, 10));
  });

  it('removes files and returns to the selection when none is left', () => {
    batch.onFilesSelected(fileEvent(pdf('a.pdf'), pdf('b.pdf')));
    batch.removeFile(item('a.pdf'));
    expect(batch.step()).toBe('review');
    batch.removeFile(item('b.pdf'));
    expect(batch.step()).toBe('select');

    batch.onFilesSelected(fileEvent(pdf('c.pdf')));
    batch.addMoreFiles();
    expect(batch.step()).toBe('select');
  });

  it('toggles the sub-invoice grouping of a supplier', () => {
    batch.onFilesSelected(fileEvent(pdf('a.pdf'), pdf('b.pdf')));

    batch.toggleGroupSubInvoices(1);
    expect(batch.files().every(f => f.groupAsSubInvoices)).toBe(true);
    batch.toggleGroupSubInvoices(1);
    expect(batch.files().every(f => !f.groupAsSubInvoices)).toBe(true);
    // Un groupe vide ne casse rien.
    batch.toggleGroupSubInvoices(42);
  });

  it('creates grouped sub-invoices under the first number and counts every outcome', () => {
    batch.onFilesSelected(fileEvent(pdf('001-Acme.pdf'), pdf('a.pdf'), pdf('b.pdf'), pdf('c.pdf')));
    batch.toggleGroupSubInvoices(1);

    batch.createAll();

    expect(invoices['upload']).toHaveBeenCalledWith(10, item('001-Acme.pdf').file);
    const requests = invoices['create'].mock.calls.map(call => call[0]);
    expect(requests.map(r => r.linkToNumber)).toEqual([null, 7, 7]);
    expect(requests[0]).toEqual(expect.objectContaining({ series: 'INVOICE', supplierId: 1 }));
    expect(batch.files().every(f => f.status === 'created')).toBe(true);
    expect(batch.progress()).toEqual({ current: 4, total: 4 });
    expect(batch.resultSummary()).toEqual({ created: 4, errors: 0 });
    expect(batch.step()).toBe('done');
  });

  it('counts failures as errors, and a failed group head fails the whole group', () => {
    invoices['upload'].mockReturnValue(throwError(() => new Error('disk')));
    invoices['create'].mockReturnValue(throwError(() => new Error('db')));
    batch.onFilesSelected(fileEvent(pdf('001-Acme.pdf'), pdf('a.pdf'), pdf('b.pdf')));
    batch.toggleGroupSubInvoices(1);

    batch.createAll();

    expect(invoices['create']).toHaveBeenCalledTimes(1);
    expect(batch.files().every(f => f.status === 'error')).toBe(true);
    expect(batch.resultSummary()).toEqual({ created: 0, errors: 3 });
  });

  it('a failed upload after creation does not undo the invoice', () => {
    invoices['upload'].mockReturnValue(throwError(() => new Error('disk')));
    batch.onFilesSelected(fileEvent(pdf('a.pdf')));

    batch.createAll();

    expect(item('a.pdf').status).toBe('created');
    expect(batch.resultSummary()).toEqual({ created: 1, errors: 0 });
  });

  it('applies no default for an unknown supplier', () => {
    const lonely = { supplierId: 99, dateScope: 'NONE', peppol: false } as BatchInvoiceItem;
    batch.applySupplierDefaults(lonely);
    expect(lonely.dateScope).toBe('NONE');
  });
});
