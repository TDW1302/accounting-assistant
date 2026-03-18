package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.entity.AiProvider;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.SupplierRepository;
import be.vercauteren.accounting.util.VatUtils;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceExtractionService {

    private final SupplierRepository supplierRepository;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    @Value("${app.anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${app.anthropic.model}")
    private String anthropicModel;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    @Value("${app.gemini.model}")
    private String geminiModel;

    private AnthropicClient anthropicClient;
    private Client geminiClient;

    @PostConstruct
    void init() {
        if (!anthropicApiKey.isBlank()) {
            anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(anthropicApiKey)
                .build();
        }
        if (!geminiApiKey.isBlank()) {
            geminiClient = Client.builder()
                .apiKey(geminiApiKey)
                .build();
        }
    }

    public InvoiceExtractionResult extract(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            log.info("Image file detected, skipping text extraction");
            return emptyResult();
        }

        String pdfText = extractText(file);
        if (pdfText.isBlank()) {
            log.warn("PDF text extraction returned empty text");
            return emptyResult();
        }

        List<Supplier> suppliers = supplierRepository.findAll();
        String prompt = buildPrompt(pdfText, suppliers);

        AiProvider provider = resolveProvider();
        String responseText;

        if (provider == AiProvider.GEMINI) {
            responseText = callGemini(prompt);
        } else {
            responseText = callClaude(prompt);
        }

        return parseResponse(responseText, suppliers);
    }

    private AiProvider resolveProvider() {
        AiProvider preferred = authService.getCurrentUser()
            .map(user -> user.getAiProvider() != null ? user.getAiProvider() : AiProvider.CLAUDE)
            .orElse(AiProvider.CLAUDE);

        if (preferred == AiProvider.GEMINI && geminiClient == null) {
            log.warn("Gemini selected but API key not configured, falling back to Claude");
            return AiProvider.CLAUDE;
        }
        if (preferred == AiProvider.CLAUDE && anthropicClient == null) {
            log.warn("Claude selected but API key not configured, falling back to Gemini");
            return AiProvider.GEMINI;
        }

        return preferred;
    }

    private String callClaude(String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
            .maxTokens(1024L)
            .addUserMessage(prompt)
            .model(anthropicModel)
            .build();

        Message message = anthropicClient.messages().create(params);

        return message.content().stream()
            .filter(block -> block.isText())
            .map(block -> block.asText().text())
            .collect(Collectors.joining());
    }

    private String callGemini(String prompt) {
        GenerateContentResponse response = geminiClient.models
            .generateContent(geminiModel, prompt, null);
        return response.text();
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String buildPrompt(String pdfText, List<Supplier> suppliers) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract structured data from this invoice. Return ONLY a JSON object with these fields:\n");
        sb.append("- type: \"PURCHASE\" or \"SALE\" (almost always PURCHASE)\n");
        sb.append("- supplierName: the supplier/vendor name as it appears on the invoice\n");
        sb.append("- enterpriseNumber: the supplier's enterprise/VAT number if present\n");
        sb.append("- amountIncVat: total amount including VAT (number)\n");
        sb.append("- amountExVat: amount excluding VAT (number)\n");
        sb.append("- vatAmount: VAT amount (number)\n");
        sb.append("- receptionDate: invoice date in YYYY-MM-DD format\n");
        sb.append("- paymentDate: due date in YYYY-MM-DD format, or null\n");
        sb.append("- dateScope: one of DAILY, MONTHLY, QUARTERLY, YEARLY, NONE\n");
        sb.append("  - DAILY for one-time purchases (restaurant, single order)\n");
        sb.append("  - MONTHLY for monthly subscriptions/services\n");
        sb.append("  - QUARTERLY for quarterly invoices\n");
        sb.append("  - YEARLY for annual subscriptions/insurance\n");
        sb.append("  - NONE if unclear\n");
        sb.append("- scopeDate: the reference date for the period in YYYY-MM-DD format (first day of the period), or null\n");
        sb.append("- comment: invoice reference number or any useful info, or null\n\n");

        sb.append("Known suppliers:\n");
        for (Supplier s : suppliers) {
            sb.append("- ID ").append(s.getId()).append(": ").append(s.getName());
            if (s.getAlias() != null) sb.append(" (alias: ").append(s.getAlias()).append(")");
            if (s.getEnterpriseNumber() != null) sb.append(" [").append(s.getEnterpriseNumber()).append("]");
            sb.append("\n");
        }
        sb.append("\nIf the supplier matches one of the known suppliers above, set supplierName to that supplier's exact name.\n\n");

        sb.append("Invoice text:\n---\n");
        sb.append(pdfText);
        sb.append("\n---\n\nRespond with ONLY the JSON object, no markdown, no explanation.");

        return sb.toString();
    }

    private InvoiceExtractionResult parseResponse(String json, List<Supplier> suppliers) {
        try {
            // Strip markdown code fences if present
            String cleaned = json.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```(?:json)?\\s*", "");
                cleaned = cleaned.replaceFirst("\\s*```$", "");
            }

            JsonNode node = objectMapper.readTree(cleaned);

            String supplierName = blankToNull(node, "supplierName");
            String enterpriseNumber = blankToNull(node, "enterpriseNumber");
            Long supplierId = matchSupplier(supplierName, enterpriseNumber, suppliers);

            InvoiceType type = parseEnum(node, "type", InvoiceType.class, InvoiceType.PURCHASE);
            DateScope dateScope = parseEnum(node, "dateScope", DateScope.class, DateScope.NONE);

            return new InvoiceExtractionResult(
                type,
                supplierId,
                supplierName,
                decimalOrNull(node, "amountIncVat"),
                decimalOrNull(node, "amountExVat"),
                decimalOrNull(node, "vatAmount"),
                dateOrNull(node, "receptionDate"),
                dateOrNull(node, "paymentDate"),
                dateScope,
                dateOrNull(node, "scopeDate"),
                blankToNull(node, "comment")
            );
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", json, e);
            return emptyResult();
        }
    }

    private Long matchSupplier(String name, String enterpriseNumber, List<Supplier> suppliers) {
        if (name == null && enterpriseNumber == null) return null;

        // Try enterprise number first (most reliable)
        if (enterpriseNumber != null) {
            String normalized = VatUtils.normalizeVat(enterpriseNumber);
            for (Supplier s : suppliers) {
                if (normalized != null && normalized.equals(VatUtils.normalizeVat(s.getEnterpriseNumber()))) {
                    return s.getId();
                }
            }
        }

        // Try name/alias match (case-insensitive)
        if (name != null) {
            String lower = name.toLowerCase();
            for (Supplier s : suppliers) {
                if (s.getName().toLowerCase().equals(lower)) return s.getId();
                if (s.getAlias() != null && s.getAlias().toLowerCase().equals(lower)) return s.getId();
            }
            // Partial match: supplier name contained in extracted name or vice versa
            for (Supplier s : suppliers) {
                String sLower = s.getName().toLowerCase();
                if (lower.contains(sLower) || sLower.contains(lower)) return s.getId();
                if (s.getAlias() != null) {
                    String aLower = s.getAlias().toLowerCase();
                    if (lower.contains(aLower) || aLower.contains(lower)) return s.getId();
                }
            }
        }

        return null;
    }

    private String blankToNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        return value.asText();
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return value.decimalValue();
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            log.debug("Failed to parse decimal field '{}': {}", field, value.asText());
            return null;
        }
    }

    private LocalDate dateOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return LocalDate.parse(value.asText());
        } catch (Exception e) {
            log.debug("Failed to parse date field '{}': {}", field, value.asText());
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(JsonNode node, String field, Class<T> enumClass, T defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return defaultValue;
        try {
            return Enum.valueOf(enumClass, value.asText());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private InvoiceExtractionResult emptyResult() {
        return new InvoiceExtractionResult(null, null, null, null, null, null, null, null, null, null, null);
    }
}
