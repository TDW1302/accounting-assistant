package be.vercauteren.accounting.service;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class FileNameGenerator {

    /**
     * Numero mis en forme: 001, 008.1, et D003 pour les depenses contractuelles.
     * Le prefixe rend la serie lisible a l'oeil, la ou la seule difference en base
     * est une colonne.
     */
    public String formatNumber(Invoice invoice) {
        StringBuilder sb = new StringBuilder();
        if (invoice.getSeries() == InvoiceSeries.EXPENSE) {
            sb.append("D");
        }
        sb.append(String.format("%03d", invoice.getNumber()));
        if (invoice.getSubNumber() != null) {
            sb.append(".").append(invoice.getSubNumber());
        }
        return sb.toString();
    }

    public String generate(Invoice invoice) {
        StringBuilder sb = new StringBuilder(formatNumber(invoice));

        // Date scope
        String datePart = formatScopeDate(invoice.getDateScope(), invoice.getScopeDate());
        if (datePart != null) {
            sb.append("-").append(datePart);
        }

        // Supplier alias
        String alias = invoice.getSupplier().getAlias();
        if (alias == null || alias.isBlank()) {
            alias = invoice.getSupplier().getName().replaceAll("\\s+", "");
        }
        sb.append("-").append(sanitize(alias));

        // File detail
        if (invoice.getFileDetail() != null && !invoice.getFileDetail().isBlank()) {
            sb.append("-").append(sanitize(invoice.getFileDetail()));
        }

        sb.append(".pdf");
        return sb.toString();
    }

    private String formatScopeDate(DateScope scope, LocalDate date) {
        if (scope == null || scope == DateScope.NONE || date == null) {
            return null;
        }
        return switch (scope) {
            case DAILY -> String.format("%02d%02d%02d",
                date.getYear() % 100, date.getMonthValue(), date.getDayOfMonth());
            case MONTHLY -> String.format("%02d%02d",
                date.getYear() % 100, date.getMonthValue());
            case QUARTERLY -> String.format("%dQ%d",
                date.getYear(), (date.getMonthValue() - 1) / 3 + 1);
            case YEARLY -> String.valueOf(date.getYear());
            case NONE -> null;
        };
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }
}
