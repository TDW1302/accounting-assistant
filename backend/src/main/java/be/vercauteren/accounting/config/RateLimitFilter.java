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
 * Limits to 5 attempts per IP per 15-minute window on login and register endpoints.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60; // 15 minutes

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = getClientIp(request);
        String key = ip + ":" + request.getRequestURI();

        AttemptRecord record = attempts.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new AttemptRecord(now, 1);
            }
            return new AttemptRecord(existing.windowStart, existing.count + 1);
        });

        if (record.count > MAX_ATTEMPTS) {
            long retryAfter = WINDOW_SECONDS - (Instant.now().getEpochSecond() - record.windowStart.getEpochSecond());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(Math.max(retryAfter, 1)));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many attempts. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return !("POST".equals(method) && (path.equals("/api/auth/login") || path.equals("/api/auth/register")));
    }

    /** Remove expired entries every 15 minutes to prevent memory leaks. */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void cleanupExpiredEntries() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry ->
            entry.getValue().windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
