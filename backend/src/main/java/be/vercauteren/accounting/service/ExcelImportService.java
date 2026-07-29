package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.ExcelImportResponse;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
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

        // Load all existing suppliers into a map (lowercase name -> Supplier)
        Map<String, Supplier> supplierMap = new HashMap<>();
        for (Supplier s : supplierRepository.findAll()) {
            supplierMap.put(s.getName().toLowerCase().trim(), s);
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

                    // Column A: number (must be a valid integer)
                    Integer number = getIntegerValue(row.getCell(0));
                    if (number == null) {
                        rowsSkipped++;
                        continue;
                    }

                    // Column B: supplier name (must not be blank)
                    String supplierName = getStringValue(row.getCell(1));
                    if (supplierName == null || supplierName.isBlank()) {
                        rowsSkipped++;
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
                    String key = cleanedName.toLowerCase().trim();
                    Supplier supplier = supplierMap.get(key);
                    if (supplier == null) {
                        supplier = supplierRepository.save(Supplier.builder()
                                .name(cleanedName)
                                .build());
                        supplierMap.put(key, supplier);
                        suppliersCreated++;
                    }

                    // Check for duplicate
                    if (invoiceRepository.existsByYearAndNumberAndSubNumberIsNull(year, number)) {
                        rowsSkipped++;
                        continue;
                    }

                    // Column C: amount TTC
                    BigDecimal amountIncVat = getDecimalValue(row.getCell(2));

                    // Column D: reception date
                    LocalDate receptionDate = getDateValue(row.getCell(3));
                    if (receptionDate == null) {
                        warnings.add("Ligne " + (r + 1) + " onglet " + year + " : date de réception manquante, utilisation de la date du jour");
                        receptionDate = LocalDate.now();
                    }

                    // Column E: payment date
                    LocalDate paymentDate = getDateValue(row.getCell(4));

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
                            .year(year)
                            .type(type)
                            .supplier(supplier)
                            .amountIncVat(amountIncVat)
                            .receptionDate(receptionDate)
                            .paymentDate(paymentDate)
                            .peppol(peppol)
                            .comment(comment)
                            .dateScope(DateScope.NONE)
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

    private Integer getIntegerValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    return (int) val;
                }
                return null;
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
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
