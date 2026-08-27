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

        AttemptRecord record = attempts.get(key);
        if (record != null && !isExpired(record, Instant.now()) && record.count >= MAX_ATTEMPTS) {
            reject(response, record);
            return;
        }

        filterChain.doFilter(request, response);

        // Seuls les echecs comptent. Compter aussi les succes verrouillait un
        // utilisateur legitime qui ouvre une sixieme session dans le quart d'heure.
        if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            registerFailure(key);
        } else if (response.getStatus() < HttpStatus.BAD_REQUEST.value()) {
            attempts.remove(key);
        }
    }

    private void registerFailure(String key) {
        attempts.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || isExpired(existing, now)) {
                return new AttemptRecord(now, 1);
            }
            return new AttemptRecord(existing.windowStart, existing.count + 1);
        });
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
     * nginx n'expose qu'une seule valeur de X-Forwarded-For, celle qu'il a lui-meme
     * etablie apres avoir resolu le vrai client via real_ip (cf. nginx.conf). Prendre
     * le dernier hop reste correct si un proxy supplementaire venait allonger la
     * chaine: les entrees precedentes, elles, viennent du client et sont falsifiables.
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            String lastHop = hops[hops.length - 1].trim();
            if (!lastHop.isEmpty()) {
                return lastHop;
            }
        }
        return request.getRemoteAddr();
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
