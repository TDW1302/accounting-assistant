package be.vercauteren.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Ce que le filtre de rate limiting voit reellement en bout de chaine. Un test
 * unitaire ne peut pas y repondre: l'adresse du client est etablie bien en amont,
 * par la strategie server.forward-headers-strategy, qui retire au passage les
 * en-tetes X-Forwarded-*. Le comportement ne se verifie donc que serveur demarre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ForwardedHeadersIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @LocalServerPort
    private int port;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private RestTestClient.ResponseSpec login(String forwardedFor) {
        return client().post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", forwardedFor)
            .header("X-Forwarded-Proto", "https")
            .body("{\"username\":\"nobody\",\"password\":\"wrong\"}")
            .exchange();
    }

    /**
     * Un mauvais mot de passe rendait 500: AuthenticationException remonte du
     * controleur et tombait dans le handler generique.
     */
    @Test
    void badCredentialsReturnUnauthorized() {
        login("203.0.113.20").expectStatus().isUnauthorized();
    }

    /**
     * Le point de S1. Le comptage se faisait sur une adresse identique pour tous
     * derriere le proxy: cinq echecs fermaient le login a tout le monde.
     */
    @Test
    void theRateLimitSeparatesClients() {
        for (int i = 0; i < 5; i++) {
            login("203.0.113.30").expectStatus().isUnauthorized();
        }
        login("203.0.113.30").expectStatus().isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);

        // Un autre client garde son propre quota.
        login("203.0.113.31").expectStatus().isUnauthorized();
    }

    /**
     * La cle est le dernier hop, celui dont le proxy se porte garant. Les entrees
     * qui le precedent viennent du client: si elles comptaient, il lui suffirait
     * d'en changer a chaque tentative pour ne jamais atteindre le quota.
     */
    @Test
    void theClientCannotChooseItsOwnRateLimitKey() {
        for (int i = 0; i < 5; i++) {
            login("9.9.9.9, 203.0.113.40").expectStatus().isUnauthorized();
        }

        // Premier hop different, dernier hop identique: toujours le meme compteur.
        login("1.1.1.1, 203.0.113.40").expectStatus()
            .isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);

        // Premier hop identique, dernier hop different: compteur distinct.
        login("9.9.9.9, 203.0.113.41").expectStatus().isUnauthorized();
    }

    /**
     * Le point de S2: sans prise en compte de X-Forwarded-Proto, request.isSecure()
     * reste faux derriere le proxy et Spring Security n'emet jamais l'en-tete HSTS
     * pourtant configure.
     */
    @Test
    void hstsIsEmittedForForwardedHttpsRequests() {
        client().get().uri("/api/invoices?year=2026")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-For", "203.0.113.60")
            .exchange()
            .expectHeader().value("Strict-Transport-Security",
                value -> assertThat(value).contains("max-age=31536000").contains("includeSubDomains"));
    }
}
