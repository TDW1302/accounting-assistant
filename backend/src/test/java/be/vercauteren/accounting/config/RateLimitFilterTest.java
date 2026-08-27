package be.vercauteren.accounting.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    /**
     * Requete de login telle qu'elle parvient au filtre: l'adresse a deja ete resolue
     * en amont a partir de X-Forwarded-For, et l'en-tete retire de la requete.
     */
    private MockHttpServletRequest loginFrom(String clientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(clientIp);
        return request;
    }

    /** Chaine rendant le statut voulu, comme le ferait le controleur en aval. */
    private static MockFilterChain chainReturning(int status) {
        return chainReturning(status, 0);
    }

    /**
     * Le vrai controleur n'est pas instantane: il compare un mot de passe BCrypt,
     * ce qui prend une centaine de millisecondes. C'est cette duree qui ouvre la
     * fenetre entre le controle du quota et son incrementation.
     */
    private static MockFilterChain chainReturning(int status, long delayMillis) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException(e);
                    }
                }
                ((HttpServletResponse) res).setStatus(status);
            }
        };
    }

    private int attempt(String clientIp, int downstreamStatus) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginFrom(clientIp), response, chainReturning(downstreamStatus));
        return response.getStatus();
    }

    @Test
    void blocksAfterFiveFailedAttempts() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt("203.0.113.7", 401)).isEqualTo(401);
        }
        assertThat(attempt("203.0.113.7", 401)).isEqualTo(429);
    }

    /**
     * Le filtre comptait toute requete, succes compris: une sixieme ouverture de
     * session dans le quart d'heure verrouillait un utilisateur legitime.
     */
    @Test
    void doesNotCountSuccessfulLogins() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(attempt("203.0.113.8", 200)).isEqualTo(200);
        }
    }

    /** Une ouverture de session reussie efface les echecs qui l'ont precedee. */
    @Test
    void aSuccessfulLoginClearsEarlierFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            attempt("203.0.113.9", 401);
        }
        assertThat(attempt("203.0.113.9", 200)).isEqualTo(200);

        for (int i = 0; i < 5; i++) {
            assertThat(attempt("203.0.113.9", 401)).isEqualTo(401);
        }
        assertThat(attempt("203.0.113.9", 401)).isEqualTo(429);
    }

    /**
     * Le point de S1: sans separation par client, cinq echecs d'un attaquant
     * fermaient le login a tout le monde.
     */
    @Test
    void countsEachClientSeparately() throws Exception {
        for (int i = 0; i < 6; i++) {
            attempt("203.0.113.10", 401);
        }
        assertThat(attempt("203.0.113.10", 401)).isEqualTo(429);
        assertThat(attempt("203.0.113.11", 401)).isEqualTo(401);
    }

    /**
     * Le filtre ne lit plus X-Forwarded-For: l'en-tete est resolu et retire en amont, et
     * la strategie native y laisse la portion non fiable de la chaine — celle que le client
     * a fournie. Le lire redonnerait a l'appelant la main sur sa propre cle de comptage.
     */
    @Test
    void ignoresAForwardedHeaderSuppliedByTheClient() throws Exception {
        for (int i = 0; i < 5; i++) {
            attempt("198.51.100.4", 401);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest spoofed = loginFrom("198.51.100.4");
        spoofed.addHeader("X-Forwarded-For", "1.2.3.4");
        filter.doFilter(spoofed, response, chainReturning(401));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
    }

    /**
     * Le quota doit tenir meme si les tentatives partent ensemble. Il se verifiait
     * puis s'incrementait apres la chaine: vingt requetes simultanees lisaient
     * toutes un compteur a zero et passaient toutes, ce qui vidait la protection
     * de son sens des lors qu'on ne les envoyait plus une a une.
     */
    @Test
    void holdsTheQuotaUnderConcurrentAttempts() throws Exception {
        int concurrent = 20;
        ExecutorService pool = Executors.newFixedThreadPool(concurrent);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger passedThrough = new AtomicInteger();
        AtomicInteger throttled = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < concurrent; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(loginFrom("203.0.113.99"), response, chainReturning(401, 100));
                int status = response.getStatus();
                if (status == 429) {
                    throttled.incrementAndGet();
                } else {
                    passedThrough.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(passedThrough.get()).isEqualTo(5);
        assertThat(throttled.get()).isEqualTo(concurrent - 5);
    }

    /** Le filtre ne s'applique qu'au login. */
    @Test
    void ignoresEverythingButTheLoginEndpoint() throws Exception {
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/invoices");
        other.setRemoteAddr("203.0.113.12");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(other, response, chainReturning(401));
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }
}
