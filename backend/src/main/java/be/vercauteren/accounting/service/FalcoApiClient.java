package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.FalcoInboundListResponse;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class FalcoApiClient {

    @Value("${app.falco.base-url}")
    private String baseUrl;

    @Value("${app.falco.api-key}")
    private String apiKey;

    @Value("${app.falco.app-secret}")
    private String appSecret;

    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("X-Falco-Api-Key", apiKey)
            .defaultHeader("X-Falco-App-Secret", appSecret)
            .build();
    }

    public FalcoInboundListResponse listInbound(LocalDate receivedAfter, LocalDate receivedBefore,
                                                  String senderName, int page, int pageSize) {
        try {
            var request = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/peppol/inbound")
                        .queryParam("page", page)
                        .queryParam("page_size", pageSize)
                        .queryParam("sort_by", "received_at")
                        .queryParam("sort_direction", "desc");
                    if (receivedAfter != null) {
                        uriBuilder.queryParam("received_after", receivedAfter.toString());
                    }
                    if (receivedBefore != null) {
                        uriBuilder.queryParam("received_before", receivedBefore.toString());
                    }
                    if (senderName != null && !senderName.isBlank()) {
                        uriBuilder.queryParam("sender_name", senderName);
                    }
                    return uriBuilder.build();
                })
                .accept(MediaType.APPLICATION_JSON);

            return request.retrieve().body(FalcoInboundListResponse.class);
        } catch (RestClientException e) {
            log.error("Failed to list inbound documents from Falco", e);
            throw new FalcoApiException("Failed to retrieve Peppol documents from Falco: " + e.getMessage(), e);
        }
    }

    public static class FalcoApiException extends RuntimeException {
        public FalcoApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
