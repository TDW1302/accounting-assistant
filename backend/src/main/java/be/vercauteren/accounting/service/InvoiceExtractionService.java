package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.entity.AiProvider;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.SupplierRepository;
import be.vercauteren.accounting.repository.UserRepository;
import be.vercauteren.accounting.util.VatUtils;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Comparator;
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

    private static final String PDF_MIME_TYPE = "application/pdf";

    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
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
        // Ordre deterministe: la liste des fournisseurs forme le prefixe cache de
        // chaque requete, et findAll() ne garantit aucun ordre. Une liste reordonnee
        // est un prefixe different, donc un cache manque en silence.
        List<Supplier> suppliers = supplierRepository.findAll().stream()
            .sorted(Comparator.comparing(Supplier::getId))
            .toList();
        AiProvider provider = resolveProvider();

        String contentType = file.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");

        // Try text extraction for PDFs
        String pdfText = null;
        if (!isImage) {
            pdfText = extractText(file);
            if (pdfText.isBlank()) {
                pdfText = null;
            }
        }

        String systemPrompt = buildSystemPrompt(suppliers);
        String responseText;

        if (pdfText != null) {
            // Text-based extraction (cheap)
            String userText = "Invoice text:\n---\n" + pdfText + "\n---";
            log.info("Using text-based extraction with {}", provider);
            responseText = (provider == AiProvider.GEMINI)
                ? callGemini(systemPrompt + "\n\n" + userText)
                : callClaude(systemPrompt, userText);
        } else {
            // Vision fallback (image or scanned PDF)
            log.info("Using vision extraction with {}", provider);
            String mimeType = isImage ? contentType : "application/pdf";
            byte[] fileBytes = file.getBytes();
            responseText = (provider == AiProvider.GEMINI)
                ? callGeminiVision(systemPrompt, fileBytes, mimeType)
                : callClaudeVision(systemPrompt, fileBytes, mimeType);
        }

        return parseResponse(responseText, suppliers);
    }

    private AiProvider resolveProvider() {
        // Load fresh from DB — the session-cached User may have stale aiProvider
        AiProvider preferred = authService.getCurrentUser()
            .map(sessionUser -> userRepository.findByUsername(sessionUser.getUsername()).orElse(sessionUser))
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

    // --- Text-based calls ---

    private String callClaude(String systemPrompt, String userText) {
        MessageCreateParams params = MessageCreateParams.builder()
            .maxTokens(1024L)
            .model(anthropicModel)
            .systemOfTextBlockParams(List.of(cacheableSystemBlock(systemPrompt)))
            .addUserMessage(userText)
            .build();

        Message message = anthropicClient.messages().create(params);

        return message.content().stream()
            .filter(block -> block.isText())
            .map(block -> block.asText().text())
            .collect(Collectors.joining());
    }

    /**
     * Le prompt systeme (consignes + liste des fournisseurs) est identique d'une
     * facture a l'autre. Le marquer cacheable le facture au dixieme du prix des le
     * deuxieme appel, alors qu'il represente l'essentiel des tokens d'entree.
     *
     * <p>Sous le minimum cacheable du modele, l'API ignore simplement la marque:
     * pas d'erreur, pas d'economie. Une modification de la liste des fournisseurs
     * invalide le cache, qui se reconstruit a l'appel suivant.
     */
    private TextBlockParam cacheableSystemBlock(String text) {
        return TextBlockParam.builder()
            .text(text)
            .cacheControl(CacheControlEphemeral.builder().build())
            .build();
    }

    private String callGemini(String prompt) {
        GenerateContentResponse response = geminiClient.models
            .generateContent(geminiModel, prompt, null);
        return response.text();
    }

    // --- Vision calls ---

    private String callClaudeVision(String systemPrompt, byte[] fileBytes, String mimeType) {
        // Base64.getEncoder() n'insere pas de saut de ligne, ce que l'API exige.
        String base64 = Base64.getEncoder().encodeToString(fileBytes);

        // Un PDF doit partir dans un bloc "document". Le bloc "image" n'accepte que
        // des types image: lui passer des octets PDF sous une etiquette JPEG fait
        // echouer la requete, et l'extraction etant best-effort, l'echec est muet.
        ContentBlockParam fileBlock = PDF_MIME_TYPE.equals(mimeType)
            ? ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(base64).build())
                .build())
            : ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                    .data(base64)
                    .mediaType(toClaudeMediaType(mimeType))
                    .build())
                .build());

        MessageCreateParams params = MessageCreateParams.builder()
            .maxTokens(1024L)
            .model(anthropicModel)
            .systemOfTextBlockParams(List.of(cacheableSystemBlock(systemPrompt)))
            .addUserMessageOfBlockParams(List.of(
                fileBlock,
                ContentBlockParam.ofText(TextBlockParam.builder()
                    .text("Extract the data from this document.")
                    .build())
            ))
            .build();

        Message message = anthropicClient.messages().create(params);

        return message.content().stream()
            .filter(block -> block.isText())
            .map(block -> block.asText().text())
            .collect(Collectors.joining());
    }

    private String callGeminiVision(String prompt, byte[] imageBytes, String mimeType) {
        Content content = Content.builder()
            .role("user")
            .parts(List.of(
                Part.fromBytes(imageBytes, mimeType),
                Part.fromText(prompt)
            ))
            .build();

        GenerateContentResponse response = geminiClient.models
            .generateContent(geminiModel, List.of(content), null);
        return response.text();
    }

    /** Types image uniquement: un PDF passe par un bloc document, pas par ici. */
    private Base64ImageSource.MediaType toClaudeMediaType(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> Base64ImageSource.MediaType.IMAGE_JPEG;
        };
    }

    // --- Helpers ---

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Consignes et liste des fournisseurs, identiques quel que soit le document.
     * Un seul prompt pour les deux chemins (texte et vision) afin que le cache soit
     * partage entre eux. Le contenu variable — texte de la facture ou fichier —
     * vit dans le message utilisateur, apres ce prefixe.
     */
    private String buildSystemPrompt(List<Supplier> suppliers) {
        StringBuilder sb = new StringBuilder();
        sb.append("You extract structured data from invoices and receipts. ");
        sb.append("Return ONLY a JSON object with these fields:\n");
        appendJsonFields(sb);
        appendSupplierList(sb, suppliers);
        sb.append("\nIf the supplier matches one of the known suppliers above, set supplierName to that supplier's exact name.\n\n");
        sb.append("Respond with ONLY the JSON object, no markdown, no explanation.");
        return sb.toString();
    }

    private void appendJsonFields(StringBuilder sb) {
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
        sb.append("- comment: invoice reference number or any useful info, or null\n");
        sb.append("- expenseCategory: if the supplier does NOT match any of the known suppliers listed below, ");
        sb.append("classify the expense into one of: ")
            .append(java.util.Arrays.stream(ExpenseCategory.values())
                .map(Enum::name).collect(Collectors.joining(", ")))
            .append(". Use AUTRE if unsure. If the supplier DOES match a known supplier, set this to null.\n\n");
    }

    private void appendSupplierList(StringBuilder sb, List<Supplier> suppliers) {
        sb.append("Known suppliers:\n");
        for (Supplier s : suppliers) {
            sb.append("- ID ").append(s.getId()).append(": ").append(s.getName());
            if (s.getAlias() != null) sb.append(" (alias: ").append(s.getAlias()).append(")");
            if (s.getEnterpriseNumber() != null) sb.append(" [").append(s.getEnterpriseNumber()).append("]");
            sb.append("\n");
        }
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
            ExpenseCategory suggestedCategory = supplierId == null
                ? parseEnum(node, "expenseCategory", ExpenseCategory.class, null)
                : null;

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
                blankToNull(node, "comment"),
                suggestedCategory
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
        return new InvoiceExtractionResult(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
