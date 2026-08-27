package be.vercauteren.accounting.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for authentication endpoints to prevent brute-force attacks.
 * Limits to 5 failed attempts per IP per 15-minute window on the login endpoint.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60; // 15 minutes

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = getClientIp(request) + ":" + request.getRequestURI();

        // Reservation atomique. Verifier puis incrementer apres coup laissait passer
        // autant de requetes concurrentes qu'un attaquant en lancait avant que la
        // premiere ne soit comptee: le quota ne tenait plus des qu'on cessait de les
        // envoyer une a une.
        AttemptRecord record = attempts.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || isExpired(existing, now)) {
                return new AttemptRecord(now, 1);
            }
            return new AttemptRecord(existing.windowStart, existing.count + 1);
        });

        if (record.count > MAX_ATTEMPTS) {
            reject(response, record);
            return;
        }

        filterChain.doFilter(request, response);

        // Une ouverture de session reussie libere la reservation et efface les echecs
        // qui l'ont precedee: sans cela un utilisateur legitime se verrouillait a sa
        // sixieme connexion du quart d'heure. Une erreur en aval la laisse en place,
        // faute de pouvoir conclure au succes.
        if (response.getStatus() < HttpStatus.BAD_REQUEST.value()) {
            attempts.remove(key);
        }
    }

    private void reject(HttpServletResponse response, AttemptRecord record) throws IOException {
        long elapsed = Instant.now().getEpochSecond() - record.windowStart.getEpochSecond();
        long retryAfter = Math.max(WINDOW_SECONDS - elapsed, 1);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Too many attempts. Try again later.\"}");
    }

    private static boolean isExpired(AttemptRecord record, Instant now) {
        return record.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return !("POST".equals(method) && path.equals("/api/auth/login"));
    }

    /** Remove expired entries every 15 minutes to prevent memory leaks. */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void cleanupExpiredEntries() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    /**
     * L'adresse est deja resolue quand la requete arrive ici: server.forward-headers-strategy
     * la fait etablir en amont a partir de X-Forwarded-For, et l'en-tete lui-meme est retire
     * de la requete. Le lire ici serait au mieux inutile, au pire nuisible — la strategie
     * native laisse dans l'en-tete reecrit la portion non fiable de la chaine, celle que le
     * client a fournie.
     *
     * <p>La confiance s'arrete donc a nginx, qui resout le vrai client par real_ip et ne
     * transmet que cette valeur (cf. nginx.conf).
     */
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
