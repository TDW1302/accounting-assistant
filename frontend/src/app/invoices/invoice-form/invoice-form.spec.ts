import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { InvoiceForm } from './invoice-form';
import { InvoiceService } from '../../services/invoice.service';
import { SupplierService } from '../../services/supplier.service';
import {
  emptyExtraction,
  fileEvent,
  testInvoice,
  testSupplier,
} from '../../../testing/fixtures';

describe('InvoiceForm', () => {
  let invoices: Record<string, ReturnType<typeof vi.fn>>;
  let suppliers: Record<string, ReturnType<typeof vi.fn>>;
  let navigate: ReturnType<typeof vi.fn>;
  let alertSpy: ReturnType<typeof vi.spyOn>;

  const monthly = testSupplier({ id: 1, defaultDateScope: 'MONTHLY', defaultPeppol: true });
  const plain = testSupplier({ id: 2, name: 'Cafe', defaultDateScope: null, defaultPeppol: false });

  function setup(params: Record<string, string> = {}, query: Record<string, string> = {}) {
    invoices = {
      get: vi.fn(() => of(testInvoice({ id: 5, series: 'INVOICE', filePath: '/x/001.pdf', subNumber: 2 }))),
      create: vi.fn(() => of(testInvoice({ id: 9 }))),
      update: vi.fn(() => of(testInvoice({ id: 5 }))),
      upload: vi.fn(() => of(testInvoice({ id: 9 }))),
      extract: vi.fn(() => of(emptyExtraction())),
    };
    suppliers = {
      list: vi.fn(() => of([monthly, plain])),
      create: vi.fn(() => of(testSupplier({ id: 3, name: 'New' }))),
    };
    TestBed.configureTestingModule({
      imports: [InvoiceForm],
      providers: [
        provideRouter([]),
        { provide: InvoiceService, useValue: invoices },
        { provide: SupplierService, useValue: suppliers },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(params), queryParamMap: convertToParamMap(query) },
          },
        },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const fixture = TestBed.createComponent(InvoiceForm);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  afterEach(() => vi.restoreAllMocks());

  it('starts a new documented purchase for today', () => {
    const form = setup();

    expect(form.isEdit).toBe(false);
    expect(form.form.get('series')!.value).toBe('INVOICE');
    expect(form.form.get('receptionDate')!.value).toBe(new Date().toISOString().substring(0, 10));
    expect(form.receptionDateLabel()).toBe('Date de réception');
    expect(form.suppliers()).toHaveLength(2);
  });

  it('loads an existing invoice and freezes its series', () => {
    const form = setup({ id: '5' });

    expect(form.isEdit).toBe(true);
    expect(invoices['get']).toHaveBeenCalledWith(5);
    expect(form.existingFilePath).toBe('/x/001.pdf');
    expect(form.form.get('series')!.disabled).toBe(true);
    expect(form.form.get('subNumber')!.value).toBe(2);
  });

  it('creates a sub-invoice in the documented ledger of the given year', () => {
    const form = setup({}, { linkTo: '8', year: '2025' });

    expect(form.linkToNumber).toBe(8);
    expect(form.form.get('year')!.disabled).toBe(true);
    expect(form.form.getRawValue().year).toBe(2025);
  });

  it('an expense has a due date, no Peppol and no document', () => {
    const form = setup();
    form.selectedFile = new File(['x'], 'a.pdf');
    form.form.patchValue({ series: 'EXPENSE', peppol: true });

    form.onSeriesChange();

    expect(form.isExpense()).toBe(true);
    expect(form.receptionDateLabel()).toBe("Date d'échéance");
    expect(form.selectedFile).toBeNull();
    expect(form.form.get('peppol')!.value).toBe(false);

    // Un retour en facture ne touche a rien.
    form.form.patchValue({ series: 'INVOICE' });
    form.selectedFile = new File(['x'], 'a.pdf');
    form.onSeriesChange();
    expect(form.selectedFile).not.toBeNull();
  });

  it('applies the supplier defaults, Peppol only for documented invoices', () => {
    const form = setup();

    form.form.patchValue({ supplierId: '1' });
    form.onSupplierChange();
    expect(form.form.get('dateScope')!.value).toBe('MONTHLY');
    expect(form.form.get('peppol')!.value).toBe(true);

    form.form.patchValue({ series: 'EXPENSE', supplierId: '1' });
    form.onSupplierChange();
    expect(form.form.get('peppol')!.value).toBe(false);

    form.form.patchValue({ series: 'INVOICE', supplierId: '2', dateScope: 'YEARLY' });
    form.onSupplierChange();
    expect(form.form.get('dateScope')!.value).toBe('YEARLY');

    // Ni fournisseur ni fournisseur inconnu: rien ne bouge.
    form.form.patchValue({ supplierId: null, peppol: true });
    form.onSupplierChange();
    form.form.patchValue({ supplierId: '99' });
    form.onSupplierChange();
    expect(form.form.get('peppol')!.value).toBe(true);
  });

  it('prefills the form from the AI extraction, the supplier rule winning on the scope', () => {
    const form = setup();
    invoices['extract'].mockReturnValue(
      of(
        emptyExtraction({
          type: 'SALE',
          supplierId: 1,
          amountIncVat: 0,
          amountExVat: 10,
          vatAmount: 2.1,
          receptionDate: '2026-04-01',
          paymentDate: '2026-04-30',
          dateScope: 'DAILY',
          scopeDate: '2026-04-01',
          comment: 'Ref 42',
        }),
      ),
    );

    form.onFileSelected(fileEvent(new File(['%PDF'], 'a.pdf', { type: 'application/pdf' })));

    const value = form.form.getRawValue();
    expect(value.type).toBe('SALE');
    expect(value.supplierId).toBe('1');
    expect(value.amountIncVat).toBe(0);
    expect(value.paymentDate).toBe('2026-04-30');
    expect(value.comment).toBe('Ref 42');
    expect(value.dateScope).toBe('MONTHLY');
    expect(form.extracting).toBe(false);
    expect(form.unmatchedSupplierName).toBeNull();
  });

  it('offers to create a supplier the AI could not match', () => {
    const form = setup();
    invoices['extract'].mockReturnValue(
      of(emptyExtraction({ supplierName: 'New', suggestedCategory: 'RESTAURANT' })),
    );
    form.onFileSelected(fileEvent(new File(['%PDF'], 'a.pdf', { type: 'application/pdf' })));
    expect(form.unmatchedSupplierName).toBe('New');
    expect(form.newSupplierCategory).toBe('RESTAURANT');

    form.createSupplierFromExtraction();

    expect(suppliers['create']).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'New', category: 'RESTAURANT', defaultPeppol: false }),
    );
    expect(form.suppliers().map(s => s.id)).toEqual([1, 2, 3]);
    expect(form.form.get('supplierId')!.value).toBe('3');
    expect(form.unmatchedSupplierName).toBeNull();
    expect(form.creatingSupplier).toBe(false);

    // Plus rien a creer.
    form.createSupplierFromExtraction();
    expect(suppliers['create']).toHaveBeenCalledTimes(1);
  });

  it('reports a failed supplier creation and lets the proposal be dismissed', () => {
    const form = setup();
    suppliers['create'].mockReturnValue(throwError(() => new Error('x')));
    form.unmatchedSupplierName = 'New';

    form.createSupplierFromExtraction();
    expect(alertSpy).toHaveBeenCalled();
    expect(form.creatingSupplier).toBe(false);

    form.dismissUnmatchedSupplier();
    expect(form.unmatchedSupplierName).toBeNull();
  });

  it('does not extract when editing, when disabled, or without file, and survives an AI failure', () => {
    const form = setup();
    form.autoExtract = false;
    form.onFileSelected(fileEvent(new File(['%PDF'], 'a.pdf', { type: 'application/pdf' })));
    form.onFileSelected(fileEvent());
    expect(invoices['extract']).not.toHaveBeenCalled();

    form.autoExtract = true;
    invoices['extract'].mockReturnValue(throwError(() => new Error('AI down')));
    form.onFileSelected(fileEvent(new File(['%PDF'], 'a.pdf', { type: 'application/pdf' })));
    expect(form.extracting).toBe(false);
  });

  it('sends a photo straight to the extraction, then uploads it with the invoice', () => {
    const form = setup();
    const photo = new File(['img'], 'ticket.webp', { type: 'image/webp' });
    invoices['extract'].mockReturnValue(of(emptyExtraction({ supplierId: 2 })));

    form.onFileSelected(fileEvent(photo));

    expect(invoices['extract']).toHaveBeenCalledWith(photo);
    expect(form.selectedFile).toBe(photo);
    expect(form.form.get('supplierId')!.value).toBe('2');

    form.save();
    expect(invoices['upload']).toHaveBeenCalledWith(9, photo);
  });

  it('refuses to save an incomplete form', () => {
    const form = setup();

    form.save();

    expect(form.submitted).toBe(true);
    expect(invoices['create']).not.toHaveBeenCalled();
  });

  it('creates then uploads the document, and normalises the request', () => {
    const form = setup({}, { linkTo: '8', year: '2026' });
    form.form.patchValue({
      supplierId: '2',
      subNumber: '3',
      amountIncVat: '12.5',
      paymentDate: '',
      comment: '',
    });
    form.selectedFile = new File(['%PDF'], 'a.pdf');

    form.save();

    expect(invoices['create']).toHaveBeenCalledWith(
      expect.objectContaining({
        supplierId: 2,
        subNumber: 3,
        amountIncVat: 12.5,
        amountExVat: null,
        paymentDate: null,
        comment: null,
        linkToNumber: 8,
        series: 'INVOICE',
      }),
    );
    expect(invoices['upload']).toHaveBeenCalledWith(9, form.selectedFile);
    expect(navigate).toHaveBeenCalledWith(['/invoices']);
  });

  it('updates without upload and reports failures', () => {
    const form = setup({ id: '5' });

    form.save();
    expect(invoices['update']).toHaveBeenCalledWith(5, expect.anything());
    expect(invoices['upload']).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/invoices']);

    invoices['update'].mockReturnValue(throwError(() => new Error('x')));
    form.save();
    expect(alertSpy).toHaveBeenCalledTimes(1);

    form.selectedFile = new File(['%PDF'], 'a.pdf');
    invoices['update'].mockReturnValue(of(testInvoice({ id: 5 })));
    invoices['upload'].mockReturnValue(throwError(() => new Error('x')));
    form.save();
    expect(alertSpy).toHaveBeenCalledTimes(2);
  });
});
