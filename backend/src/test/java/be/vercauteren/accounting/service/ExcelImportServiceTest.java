package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;

import be.vercauteren.accounting.service.ExcelImportService.InvoiceNumber;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Parsing de la colonne A du facturier. Pas de contexte Spring: la methode est
 * statique et sans dependance, donc testable directement.
 */
class ExcelImportServiceTest {

	private Workbook workbook;
	private Row row;

	@BeforeEach
	void setUp() {
		workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("2026");
		row = sheet.createRow(0);
	}

	@AfterEach
	void tearDown() throws Exception {
		workbook.close();
	}

	private Cell numeric(double value) {
		Cell cell = row.createCell(row.getPhysicalNumberOfCells());
		cell.setCellValue(value);
		return cell;
	}

	private Cell text(String value) {
		Cell cell = row.createCell(row.getPhysicalNumberOfCells());
		cell.setCellValue(value);
		return cell;
	}

	@Test
	void readsPlainIntegerWithoutSubNumber() {
		assertThat(ExcelImportService.parseNumber(numeric(167)))
			.isEqualTo(new InvoiceNumber(167, null));
	}

	@Test
	void readsSubNumberFromDecimal() {
		assertThat(ExcelImportService.parseNumber(numeric(54.1)))
			.isEqualTo(new InvoiceNumber(54, 1));
		assertThat(ExcelImportService.parseNumber(numeric(54.2)))
			.isEqualTo(new InvoiceNumber(54, 2));
	}

	@Test
	void doesNotFallIntoScientificNotationForLargeWholeNumbers() {
		// BigDecimal.valueOf(100000.0).stripTrailingZeros() vaut 1E+5
		assertThat(ExcelImportService.parseNumber(numeric(100000)))
			.isEqualTo(new InvoiceNumber(100000, null));
	}

	@Test
	void readsNumberStoredAsText() {
		assertThat(ExcelImportService.parseNumber(text("54.1")))
			.isEqualTo(new InvoiceNumber(54, 1));
		assertThat(ExcelImportService.parseNumber(text(" 42 ")))
			.isEqualTo(new InvoiceNumber(42, null));
	}

	@Test
	void acceptsCommaAsDecimalSeparator() {
		assertThat(ExcelImportService.parseNumber(text("54,1")))
			.isEqualTo(new InvoiceNumber(54, 1));
	}

	@Test
	void treatsTrailingZeroesAsTheSameSubNumber() {
		assertThat(ExcelImportService.parseNumber(text("54.10")))
			.isEqualTo(new InvoiceNumber(54, 1));
		assertThat(ExcelImportService.parseNumber(text("54.0")))
			.isEqualTo(new InvoiceNumber(54, null));
	}

	@Test
	void supplierKeyIgnoresCaseAndSpaces() {
		// Les variantes relevees dans le facturier doivent designer une seule fiche
		assertThat(ExcelImportService.normaliseSupplierKey("Mobile Viking"))
			.isEqualTo(ExcelImportService.normaliseSupplierKey("MobileViking"))
			.isEqualTo(ExcelImportService.normaliseSupplierKey("Mobile viking"));
		assertThat(ExcelImportService.normaliseSupplierKey("Oliver James"))
			.isEqualTo(ExcelImportService.normaliseSupplierKey("OliverJames"));
		assertThat(ExcelImportService.normaliseSupplierKey(" 7 ici "))
			.isEqualTo(ExcelImportService.normaliseSupplierKey("7Ici"));
		assertThat(ExcelImportService.normaliseSupplierKey("Pasta Fresca"))
			.isEqualTo(ExcelImportService.normaliseSupplierKey("PastaFresca"));
	}

	@Test
	void supplierKeyStillSeparatesDifferentNames() {
		assertThat(ExcelImportService.normaliseSupplierKey("Acerta"))
			.isNotEqualTo(ExcelImportService.normaliseSupplierKey("Acerta Group"));
	}

	@Test
	void rejectsWhatIsNotAnInvoiceNumber() {
		assertThat(ExcelImportService.parseNumber(null)).isNull();
		assertThat(ExcelImportService.parseNumber(text("Q2"))).isNull();
		assertThat(ExcelImportService.parseNumber(text(""))).isNull();
		assertThat(ExcelImportService.parseNumber(numeric(0))).isNull();
		assertThat(ExcelImportService.parseNumber(numeric(-3))).isNull();
	}
}
