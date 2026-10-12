package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.dto.SupplierAiData;
import be.vercauteren.accounting.entity.AiProvider;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.repository.SupplierRepository;
import be.vercauteren.accounting.repository.UserRepository;
import be.vercauteren.accounting.util.InMemoryMultipartFile;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateContentResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extraction par IA, sans reseau: les clients Claude et Gemini sont simules, ce
 * qui permet de verifier ce qui leur est envoye — texte, document ou image — et
 * la lecture de leur reponse, y compris quand elle est mal formee.
 */
class InvoiceExtractionServiceTest {

    private static final String SONNET = "claude-sonnet-5-5";

    private SupplierRepository supplierRepository;
    private UserRepository userRepository;
    private AuthService authService;
    private InvoiceExtractionService service;

    private MessageService claudeMessages;
    private Models geminiModels;

    private final Supplier ondes = Supplier.builder()
        .id(1L).name("Ondes").alias("Od").enterpriseNumber("0456.789.034").build();
    private final Supplier cafe = Supplier.builder()
        .id(2L).name("Cafe du Marche").alias("CafeDuMarche").build();

    @BeforeEach
    void setUp() {
        supplierRepository = mock(SupplierRepository.class);
        userRepository = mock(UserRepository.class);
        authService = mock(AuthService.class);
        service = new InvoiceExtractionService(
            supplierRepository, userRepository, JsonMapper.builder().build(), authService);
        ReflectionTestUtils.setField(service, "anthropicApiKey", "");
        ReflectionTestUtils.setField(service, "geminiApiKey", "");
        ReflectionTestUtils.setField(service, "anthropicModel", SONNET);
        ReflectionTestUtils.setField(service, "geminiModel", "gemini-2.5-flash");

        when(supplierRepository.findAll()).thenReturn(List.of(cafe, ondes));
        when(authService.getCurrentUser()).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------ fakes

    private void withClaude() {
        AnthropicClient client = mock(AnthropicClient.class);
        claudeMessages = mock(MessageService.class);
        when(client.messages()).thenReturn(claudeMessages);
        ReflectionTestUtils.setField(service, "anthropicClient", client);
    }

    private void withGemini() throws Exception {
        Client client = mock(Client.class);
        geminiModels = mock(Models.class);
        // Champ public final, que le mock ne renseigne pas.
        Field models = Client.class.getField("models");
        models.setAccessible(true);
        models.set(client, geminiModels);
        ReflectionTestUtils.setField(service, "geminiClient", client);
    }

    private void claudeAnswers(String json) {
        TextBlock text = mock(TextBlock.class);
        when(text.text()).thenReturn(json);
        ContentBlock textBlock = mock(ContentBlock.class);
        when(textBlock.isText()).thenReturn(true);
        when(textBlock.asText()).thenReturn(text);
        ContentBlock thinkingBlock = mock(ContentBlock.class);
        when(thinkingBlock.isText()).thenReturn(false);
        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(thinkingBlock, textBlock));
        when(claudeMessages.create(any(MessageCreateParams.class))).thenReturn(message);
    }

    private GenerateContentResponse geminiResponse(String json) {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(json);
        return response;
    }

    private MessageCreateParams sentToClaude() {
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(claudeMessages).create(captor.capture());
        return captor.getValue();
    }

    private void prefers(AiProvider provider) {
        User sessionUser = User.builder().username("alice").aiProvider(AiProvider.CLAUDE).build();
        User stored = User.builder().username("alice").aiProvider(provider).build();
        when(authService.getCurrentUser()).thenReturn(Optional.of(sessionUser));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));
    }

    // ------------------------------------------------------------ documents

    private static InMemoryMultipartFile textPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new InMemoryMultipartFile("file", "invoice.pdf", "application/pdf", out.toByteArray());
        }
    }

    /** Un PDF sans couche texte, comme un scan. */
    private static InMemoryMultipartFile scannedPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new InMemoryMultipartFile("file", "scan.pdf", "application/pdf", out.toByteArray());
        }
    }

    private static InMemoryMultipartFile image(String contentType) {
        return new InMemoryMultipartFile("file", "photo", contentType, new byte[] {1, 2, 3});
    }

    private static final String FULL_ANSWER = """
        {"type": "SALE", "supplierName": "Whatever", "enterpriseNumber": "BE 0456 789 034",
         "amountIncVat": 121.5, "amountExVat": "100.41", "vatAmount": "n/a",
         "receptionDate": "2026-03-01", "paymentDate": "not a date", "dateScope": "MONTHLY",
         "scopeDate": "2026-03-01", "comment": "  ", "expenseCategory": "TELECOM"}""";

    // ------------------------------------------------------------ tests

    @Nested
    class Claude {

        @BeforeEach
        void configure() {
            withClaude();
        }

        @Test
        void aTextPdfIsSentAsTextWithTheSupplierListAndParsed() throws Exception {
            claudeAnswers(FULL_ANSWER);

            InvoiceExtractionResult result = service.extract(textPdf("Facture Ondes 121,50 EUR"));

            assertThat(result.type()).isEqualTo(InvoiceType.SALE);
            assertThat(result.supplierId()).isEqualTo(1L);
            assertThat(result.amountIncVat()).isEqualByComparingTo("121.5");
            assertThat(result.amountExVat()).isEqualByComparingTo("100.41");
            assertThat(result.vatAmount()).isNull();
            assertThat(result.receptionDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(result.paymentDate()).isNull();
            assertThat(result.dateScope()).isEqualTo(DateScope.MONTHLY);
            assertThat(result.comment()).isNull();
            // Fournisseur reconnu: la categorie suggeree n'a pas lieu d'etre.
            assertThat(result.suggestedCategory()).isNull();

            MessageCreateParams params = sentToClaude();
            String system = params.system().orElseThrow().asTextBlockParams().getFirst().text();
            // Liste triee par identifiant, avec alias et numero.
            assertThat(system).contains("- ID 1: Ondes (alias: Od) [0456.789.034]\n- ID 2: Cafe du Marche");
            assertThat(params.thinking()).isPresent();
            assertThat(params.messages().getFirst().content().asString())
                .contains("Facture Ondes 121,50 EUR");
        }

        @Test
        void aScannedPdfIsSentAsADocument() throws Exception {
            claudeAnswers("{}");

            service.extract(scannedPdf());

            List<ContentBlockParam> blocks = sentToClaude().messages().getFirst().content().asBlockParams();
            assertThat(blocks.getFirst().isDocument()).isTrue();
            assertThat(blocks.get(1).asText().text()).isEqualTo("Extract the data from this document.");
        }

        @ParameterizedTest
        @CsvSource({
            "image/png, IMAGE_PNG",
            "image/gif, IMAGE_GIF",
            "image/webp, IMAGE_WEBP",
            "image/jpeg, IMAGE_JPEG",
            "image/bmp, IMAGE_JPEG",
        })
        void anImageIsSentAsAnImageWithItsMediaType(String contentType, String expected) throws Exception {
            claudeAnswers("{}");

            service.extract(image(contentType));

            ContentBlockParam block = sentToClaude().messages().getFirst().content().asBlockParams().getFirst();
            assertThat(block.isImage()).isTrue();
            Base64ImageSource.MediaType mediaType = block.asImage().source().asBase64().mediaType();
            assertThat(mediaType.known().name()).isEqualTo(expected);
        }

        @Test
        void anotherModelKeepsItsDefaultThinking() throws Exception {
            ReflectionTestUtils.setField(service, "anthropicModel", "claude-opus-5-5");
            claudeAnswers("{}");

            service.extract(image("image/png"));

            assertThat(sentToClaude().thinking()).isEmpty();
        }

        @Test
        void anUnknownSupplierComesWithASuggestedCategory() throws Exception {
            claudeAnswers("""
                {"type": "BOGUS", "supplierName": "Totally New Shop", "enterpriseNumber": null,
                 "dateScope": null, "expenseCategory": "RESTAURANT", "amountIncVat": null}""");

            InvoiceExtractionResult result = service.extract(image("image/png"));

            assertThat(result.supplierId()).isNull();
            assertThat(result.supplierName()).isEqualTo("Totally New Shop");
            assertThat(result.type()).isEqualTo(InvoiceType.PURCHASE);
            assertThat(result.dateScope()).isEqualTo(DateScope.NONE);
            assertThat(result.suggestedCategory()).isEqualTo(ExpenseCategory.RESTAURANT);
        }

        @ParameterizedTest
        @CsvSource({
            "ondes, 1",
            "cafedumarche, 2",
            "Ondes Belgium SA, 1",
            "Cafe du Marche Bruxelles, 2",
            "Od, 1",
            "CafeDuMarche Ixelles, 2",
        })
        void suppliersMatchByNameAliasOrInclusion(String name, long expectedId) throws Exception {
            claudeAnswers("{\"supplierName\": \"" + name + "\"}");

            assertThat(service.extract(image("image/png")).supplierId()).isEqualTo(expectedId);
        }

        @Test
        void anUnknownEnterpriseNumberFallsBackOnTheName() throws Exception {
            claudeAnswers("{\"supplierName\": \"Cafe du Marche\", \"enterpriseNumber\": \"0765.432.146\"}");

            assertThat(service.extract(image("image/png")).supplierId()).isEqualTo(2L);
        }

        @Test
        void aMalformedAnswerGivesAnEmptyResult() throws Exception {
            claudeAnswers("not json at all");

            InvoiceExtractionResult result = service.extract(image("image/png"));

            assertThat(result).isEqualTo(new InvoiceExtractionResult(
                null, null, null, null, null, null, null, null, null, null, null, null));
        }

        @Test
        void supplierDataNamesThePartyAndKeepsTheSchemaConstant() throws Exception {
            claudeAnswers("{\"enterpriseNumber\": \"0456.789.034\", \"category\": \"TELECOM\"}");

            SupplierAiData data = service.extractSupplierData(textPdf("Ondes SA"), "Ondes");

            assertThat(data).isEqualTo(new SupplierAiData("0456.789.034", ExpenseCategory.TELECOM));
            MessageCreateParams params = sentToClaude();
            assertThat(params.messages().getFirst().content().asString()).startsWith("Party: Ondes\n\n");
            assertThat(params.system().orElseThrow().asTextBlockParams().getFirst().text())
                .contains("You identify a business party");
        }

        @Test
        void unreadableSupplierDataIsEmpty() throws Exception {
            claudeAnswers("{oops");

            assertThat(service.extractSupplierData(image("image/jpeg"), "Ondes"))
                .isEqualTo(new SupplierAiData(null, null));
        }

        @Test
        void aGeminiPreferenceWithoutGeminiKeyStaysOnClaude() throws Exception {
            prefers(AiProvider.GEMINI);
            claudeAnswers("{}");

            service.extract(image("image/png"));

            verify(claudeMessages).create(any(MessageCreateParams.class));
        }

        @Test
        void aSessionUserGoneFromTheDatabaseKeepsItsOwnPreference() throws Exception {
            User sessionUser = User.builder().username("ghost").aiProvider(null).build();
            when(authService.getCurrentUser()).thenReturn(Optional.of(sessionUser));
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            claudeAnswers("{}");

            service.extract(image("image/png"));

            verify(claudeMessages).create(any(MessageCreateParams.class));
        }
    }

    @Nested
    class Gemini {

        @BeforeEach
        void configure() throws Exception {
            withGemini();
        }

        @Test
        void aTextPdfGoesToGeminiAsASinglePrompt() throws Exception {
            prefers(AiProvider.GEMINI);
            GenerateContentResponse response = geminiResponse(FULL_ANSWER);
            when(geminiModels.generateContent(eq("gemini-2.5-flash"), anyString(), any())).thenReturn(response);

            InvoiceExtractionResult result = service.extract(textPdf("Facture"));

            assertThat(result.supplierId()).isEqualTo(1L);
            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(geminiModels).generateContent(eq("gemini-2.5-flash"), prompt.capture(), any());
            assertThat(prompt.getValue()).contains("Known suppliers:").contains("Invoice text:\n---\nFacture");
        }

        @Test
        void anImageGoesToGeminiAsInlineData() throws Exception {
            prefers(AiProvider.GEMINI);
            GenerateContentResponse response = geminiResponse("{\"supplierName\": \"Ondes\"}");
            when(geminiModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

            assertThat(service.extract(image("image/png")).supplierId()).isEqualTo(1L);
        }

        @Test
        void aClaudePreferenceWithoutClaudeKeyFallsBackOnGemini() throws Exception {
            GenerateContentResponse response = geminiResponse("{}");
            when(geminiModels.generateContent(anyString(), anyList(), any())).thenReturn(response);

            service.extract(scannedPdf());

            verify(geminiModels).generateContent(anyString(), anyList(), any());
            verify(geminiModels, never()).generateContent(anyString(), anyString(), any());
        }
    }

    @Test
    void clientsAreBuiltOnlyForConfiguredKeys() {
        service.init();
        assertThat(ReflectionTestUtils.getField(service, "anthropicClient")).isNull();
        assertThat(ReflectionTestUtils.getField(service, "geminiClient")).isNull();

        ReflectionTestUtils.setField(service, "anthropicApiKey", "sk-test");
        ReflectionTestUtils.setField(service, "geminiApiKey", "gm-test");
        service.init();
        assertThat(ReflectionTestUtils.getField(service, "anthropicClient")).isNotNull();
        assertThat(ReflectionTestUtils.getField(service, "geminiClient")).isNotNull();
    }
}
