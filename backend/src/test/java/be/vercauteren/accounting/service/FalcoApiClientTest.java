package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.vercauteren.accounting.dto.FalcoInboundListResponse;
import be.vercauteren.accounting.service.FalcoApiClient.FalcoApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Le client Falco face a un faux serveur local: ce qui part (en-tetes, filtres)
 * et ce qui revient (snake_case, erreurs).
 */
class FalcoApiClientTest {

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private final List<Map<String, List<String>>> headers = new ArrayList<>();
    private int status = 200;
    private String body = """
        {"data": [{"id": "doc-1", "received_at": "2026-03-01", "sender_name": "Ondes SA",
                   "sender_vat_number": "BE0456789034", "amount": "121.00", "is_credit_note": false}]}""";

    private FalcoApiClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/peppol/inbound", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            headers.add(Map.copyOf(exchange.getRequestHeaders()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        client = new FalcoApiClient();
        ReflectionTestUtils.setField(client, "baseUrl",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        ReflectionTestUtils.setField(client, "apiKey", "key-123");
        ReflectionTestUtils.setField(client, "appSecret", "secret-456");
        client.init();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheCredentialsAndPagingAndReadsSnakeCase() {
        FalcoInboundListResponse response = client.listInbound(null, null, " ", 2, 50);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().getFirst().senderName()).isEqualTo("Ondes SA");
        assertThat(response.data().getFirst().receivedAt()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(queries.getFirst())
            .isEqualTo("page=2&page_size=50&sort_by=received_at&sort_direction=desc");
        assertThat(headers.getFirst().get("X-falco-api-key")).containsExactly("key-123");
        assertThat(headers.getFirst().get("X-falco-app-secret")).containsExactly("secret-456");
    }

    @Test
    void forwardsTheOptionalFilters() {
        client.listInbound(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "Ondes", 0, 10);

        assertThat(queries.getFirst())
            .contains("received_after=2026-01-01")
            .contains("received_before=2026-02-01")
            .contains("sender_name=Ondes");
    }

    @Test
    void anErrorResponseBecomesAFalcoApiException() {
        status = 500;
        body = "{\"error\": \"boom\"}";

        assertThatThrownBy(() -> client.listInbound(null, null, null, 0, 10))
            .isInstanceOf(FalcoApiException.class)
            .hasMessageContaining("Failed to retrieve Peppol documents from Falco");
    }
}
