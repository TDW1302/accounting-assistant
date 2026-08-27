package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.ExcelImportResponse;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private final InvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    public ExcelImportResponse importExcel(MultipartFile file) throws IOException {
        int suppliersCreated = 0;
        int invoicesImported = 0;
        int rowsSkipped = 0;
        List<String> warnings = new ArrayList<>();

        // Load all existing suppliers into a map (normalised name -> Supplier)
        Map<String, Supplier> supplierMap = new HashMap<>();
        for (Supplier s : supplierRepository.findAll()) {
            supplierMap.put(normaliseSupplierKey(s.getName()), s);
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName().trim();

                int year;
                try {
                    year = Integer.parseInt(sheetName);
                } catch (NumberFormatException e) {
                    warnings.add("Onglet ignoré (nom non-numérique) : " + sheetName);
                    continue;
                }

                boolean is2026 = year == 2026;

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        rowsSkipped++;
                        continue;
                    }

                    // Column A: number, optionally suffixed with a sub-number (54.1)
                    InvoiceNumber parsed = parseNumber(row.getCell(0));
                    if (parsed == null) {
                        rowsSkipped++;
                        if (rowCarriesData(row)) {
                            warnings.add(describe(year, r) + " : numero manquant ou illisible en colonne A, ligne ignoree alors qu'elle contient des donnees");
                        }
                        continue;
                    }
                    Integer number = parsed.number();
                    Integer subNumber = parsed.subNumber();

                    // Column B: supplier name (must not be blank)
                    String supplierName = getStringValue(row.getCell(1));
                    if (supplierName == null || supplierName.isBlank()) {
                        rowsSkipped++;
                        if (rowCarriesData(row)) {
                            warnings.add(describe(year, r) + " : fournisseur manquant en colonne B, ligne ignoree alors qu'elle contient des donnees");
                        }
                        continue;
                    }
                    supplierName = supplierName.trim();

                    // Determine type: Oliver James → SALE
                    boolean isOliverJames = supplierName.toLowerCase().contains("oliver james")
                            || supplierName.toLowerCase().equals("oliverjames");
                    InvoiceType type = isOliverJames ? InvoiceType.SALE : InvoiceType.PURCHASE;

                    // Clean supplier name: remove "Facture " prefix for Oliver James entries
                    String cleanedName = supplierName;
                    if (isOliverJames && cleanedName.toLowerCase().startsWith("facture ")) {
                        cleanedName = cleanedName.substring("facture ".length()).trim();
                    }

                    // Find or create supplier
                    String key = normaliseSupplierKey(cleanedName);
                    Supplier supplier = supplierMap.get(key);
                    if (supplier == null) {
                        supplier = supplierRepository.save(Supplier.builder()
                                .name(cleanedName)
                                .build());
                        supplierMap.put(key, supplier);
                        suppliersCreated++;
                    }

                    // Check for duplicate
                    boolean duplicate = subNumber == null
                            ? invoiceRepository.existsByYearAndNumberAndSubNumberIsNull(year, number)
                            : invoiceRepository.existsByYearAndNumberAndSubNumber(year, number, subNumber);
                    if (duplicate) {
                        rowsSkipped++;
                        warnings.add(describe(year, r) + " : numero " + formatNumber(number, subNumber)
                                + " deja present, ligne ignoree (fournisseur " + cleanedName + ")");
                        continue;
                    }

                    // Column C: amount TTC
                    BigDecimal amountIncVat = getDecimalValue(row.getCell(2));

                    // Column E: payment date
                    LocalDate paymentDate = getDateValue(row.getCell(4));

                    // Column D: reception date. Fall back to the payment date rather than
                    // today, which would date a 2025 invoice in the current year.
                    LocalDate receptionDate = getDateValue(row.getCell(3));
                    if (receptionDate == null) {
                        if (paymentDate != null) {
                            receptionDate = paymentDate;
                            warnings.add(describe(year, r) + " : date de reception manquante, date de paiement utilisee ("
                                    + paymentDate + ")");
                        } else {
                            receptionDate = LocalDate.of(year, 1, 1);
                            warnings.add(describe(year, r) + " : dates de reception et de paiement manquantes, 01/01/"
                                    + year + " utilise");
                        }
                    }

                    // Column F+: comment and peppol flag
                    boolean peppol = false;
                    String comment = null;

                    if (is2026) {
                        // 2026: col F = Peppol (V), col G = Commentaire
                        String colF = getStringValue(row.getCell(5));
                        peppol = colF != null && colF.trim().equalsIgnoreCase("V");
                        comment = getStringValue(row.getCell(6));
                    } else {
                        // 2024-2025: col F = Commentaire
                        comment = getStringValue(row.getCell(5));
                    }

                    Invoice invoice = Invoice.builder()
                            .number(number)
                            .subNumber(subNumber)
                            .year(year)
                            .type(type)
                            .supplier(supplier)
                            .amountIncVat(amountIncVat)
                            .receptionDate(receptionDate)
                            .paymentDate(paymentDate)
                            .peppol(peppol)
                            .comment(comment)
                            .dateScope(DateScope.NONE)
                            // Seule origine sans auteur, et la seule que la contrainte
                            // ck_invoice_author_required laisse passer avec createdBy nul:
                            // ces factures preexistent a l'application.
                            .source(InvoiceSource.EXCEL_IMPORT)
                            .build();

                    invoiceRepository.save(invoice);
                    invoicesImported++;
                }
            }
        }

        log.info("Excel import completed: {} suppliers created, {} invoices imported, {} rows skipped",
                suppliersCreated, invoicesImported, rowsSkipped);

        return new ExcelImportResponse(suppliersCreated, invoicesImported, rowsSkipped, warnings);
    }

    /**
     * Cle de rapprochement des fournisseurs. Ignore la casse ET les espaces, pour
     * que "Mobile Viking", "Mobile viking" et "MobileViking" designent la meme fiche.
     */
    static String normaliseSupplierKey(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("\\s+", "");
    }

    /** Numero de facture, avec sous-numero optionnel: 54 ou 54.1. */
    record InvoiceNumber(Integer number, Integer subNumber) {}

    /**
     * Lit la colonne A. Un entier donne un numero simple, une decimale donne un
     * numero avec sous-numero: 54.1 -> numero 54, sous-numero 1. Retourne null
     * si la cellule ne contient pas un numero exploitable.
     */
    static InvoiceNumber parseNumber(Cell cell) {
        if (cell == null) return null;

        String raw;
        if (cell.getCellType() == CellType.NUMERIC) {
            // toPlainString evite la notation scientifique: 167.0 -> "167", 54.1 -> "54.1"
            raw = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        } else if (cell.getCellType() == CellType.STRING) {
            raw = cell.getStringCellValue().trim().replace(',', '.');
        } else {
            return null;
        }
        if (raw.isEmpty()) return null;

        String intPart = raw;
        String fracPart = null;
        int dot = raw.indexOf('.');
        if (dot >= 0) {
            intPart = raw.substring(0, dot);
            fracPart = raw.substring(dot + 1);
        }

        try {
            int number = Integer.parseInt(intPart);
            if (number <= 0) return null;

            if (fracPart == null || fracPart.isEmpty()) {
                return new InvoiceNumber(number, null);
            }
            // "54.10" et "54.1" designent le meme sous-numero 1
            String trimmed = fracPart.replaceAll("0+$", "");
            if (trimmed.isEmpty()) {
                return new InvoiceNumber(number, null);
            }
            int subNumber = Integer.parseInt(trimmed);
            if (subNumber <= 0) return null;
            return new InvoiceNumber(number, subNumber);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatNumber(Integer number, Integer subNumber) {
        return subNumber == null ? String.valueOf(number) : number + "." + subNumber;
    }

    /** POI indexe les lignes a partir de 0, Excel les affiche a partir de 1. */
    private static String describe(int year, int rowIndex) {
        return "Onglet " + year + " ligne " + (rowIndex + 1);
    }

    /** Vrai si la ligne porte autre chose qu'un numero, donc si l'ignorer perd de l'information. */
    private static boolean rowCarriesData(Row row) {
        for (int c = 1; c <= 6; c++) {
            Cell cell = row.getCell(c);
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) continue;
            return true;
        }
        return false;
    }

    private String getStringValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            String val = cell.getStringCellValue();
            return val == null || val.isBlank() ? null : val.trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf(cell.getNumericCellValue());
        }
        return null;
    }

    private BigDecimal getDecimalValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim().replace(",", ".");
                if (s.isEmpty()) return null;
                return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private LocalDate getDateValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) return null;
                return LocalDate.parse(s);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
