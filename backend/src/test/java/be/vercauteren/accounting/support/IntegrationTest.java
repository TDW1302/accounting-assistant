package be.vercauteren.accounting.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Periodicity;
import be.vercauteren.accounting.entity.RecurringExpense;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.RecurringExpenseRepository;
import be.vercauteren.accounting.repository.SupplierRepository;
import be.vercauteren.accounting.repository.UserRepository;
import be.vercauteren.accounting.security.CustomUserDetails;
import be.vercauteren.accounting.service.FalcoApiClient;
import be.vercauteren.accounting.service.InvoiceExtractionService;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Base des tests d'API: contexte complet, vraie chaine Spring Security, vrai
 * PostgreSQL. Seuls les appels sortants — l'IA et Falco — sont remplaces.
 *
 * <p>Le conteneur est demarre une fois pour toutes les classes qui en heritent,
 * et toutes partagent la meme configuration: le contexte Spring est donc mis en
 * cache et ne redemarre pas d'une classe a l'autre. En contrepartie, la base est
 * remise a zero avant chaque test.
 *
 * <p>Peppol est active ici, contrairement au defaut: c'est le seul moyen de
 * faire exister ses beans, et le client Falco est de toute facon un mock.
 */
@SpringBootTest(properties = {
    "app.falco.enabled=true",
    "app.admin.username=" + IntegrationTest.ADMIN_USERNAME,
    "app.admin.password=Adm1n!Initial",
    "app.admin.email=admin@test.local",
    "app.company.enterprise-number=" + IntegrationTest.OWN_ENTERPRISE_NUMBER,
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String ADMIN_USERNAME = "admin";
    public static final String OWN_ENTERPRISE_NUMBER = "0999.999.922";
    public static final String PASSWORD = "Secr3t!Pass";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Path ROOT;

    static {
        POSTGRES.start();
        try {
            ROOT = Files.createTempDirectory("accounting-it");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static final Path UPLOADS = ROOT.resolve("uploads");
    protected static final Path INBOX = ROOT.resolve("inbox");

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        registry.add("app.upload.directory", UPLOADS::toString);
        registry.add("app.inbox.directory", INBOX::toString);
    }

    @MockitoBean
    protected InvoiceExtractionService extractionService;

    @MockitoBean
    protected FalcoApiClient falcoApiClient;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SupplierRepository supplierRepository;

    @Autowired
    protected InvoiceRepository invoiceRepository;

    @Autowired
    protected RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetState() throws IOException {
        jdbc.execute("TRUNCATE invoice, recurring_expense, supplier RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM app_user WHERE username NOT IN ('system', ?)", ADMIN_USERNAME);
        deleteRecursively(UPLOADS);
        deleteRecursively(INBOX);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    // ------------------------------------------------------------ fixtures

    protected User admin() {
        return userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
    }

    protected User newUser(String username, UserRole role) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(User.builder()
            .username(username)
            .email(username + "@test.local")
            .password(passwordEncoder.encode(PASSWORD))
            .role(role)
            .enabled(true)
            .passwordChangedAt(now)
            .passwordExpiresAt(now.plusMonths(3))
            .createdAt(now)
            .build());
    }

    /** Authentifie la requete au nom de cet utilisateur, comme le ferait la session. */
    protected static RequestPostProcessor as(User user) {
        return user(new CustomUserDetails(user));
    }

    protected Supplier supplier(String name) {
        return supplier(name, null, null, null);
    }

    protected Supplier supplier(String name, String alias, String enterpriseNumber, ExpenseCategory category) {
        return supplierRepository.save(Supplier.builder()
            .name(name)
            .alias(alias)
            .enterpriseNumber(enterpriseNumber)
            .category(category)
            .build());
    }

    protected Invoice.InvoiceBuilder invoiceOf(Supplier supplier, User author, int year, int number) {
        return Invoice.builder()
            .number(number)
            .series(InvoiceSeries.INVOICE)
            .year(year)
            .type(InvoiceType.PURCHASE)
            .supplier(supplier)
            .receptionDate(LocalDate.of(year, 1, 15))
            .dateScope(DateScope.NONE)
            .createdBy(author)
            .source(author == null ? InvoiceSource.EXCEL_IMPORT : InvoiceSource.MANUAL)
            .amountIncVat(new BigDecimal("121.00"));
    }

    protected Invoice save(Invoice.InvoiceBuilder builder) {
        return invoiceRepository.save(builder.build());
    }

    protected RecurringExpense.RecurringExpenseBuilder recurringOf(Supplier supplier, Periodicity periodicity,
                                                                   LocalDate startDate) {
        return RecurringExpense.builder()
            .label("Loyer bureau")
            .supplier(supplier)
            .amountIncVat(new BigDecimal("800.00"))
            .periodicity(periodicity)
            .startDate(startDate)
            .active(true)
            .paidOnDueDate(true)
            .createdBy(admin());
    }

    protected RecurringExpense save(RecurringExpense.RecurringExpenseBuilder builder) {
        return recurringExpenseRepository.save(builder.build());
    }

    // ------------------------------------------------------------ http helpers

    protected String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    protected static <T> T read(MvcResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static long idOf(MvcResult result) {
        return ((Number) read(result, "$.id")).longValue();
    }

    // ------------------------------------------------------------ documents

    /** Un PDF minimal: FileSignatures n'en lit que l'en-tete. */
    protected static byte[] pdfBytes() {
        return "%PDF-1.4\n%fake document\n%%EOF".getBytes();
    }

    /** Un vrai PNG de 2x2, lisible par ImageIO pour la conversion en PDF. */
    protected static byte[] pngBytes() {
        try {
            var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
