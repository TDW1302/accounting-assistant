package be.vercauteren.accounting.config;

import be.vercauteren.accounting.service.AuthService;
import be.vercauteren.accounting.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un mot de passe expire n'ouvre plus que la porte du changement de mot de
 * passe. Sans ce filtre l'expiration ne tient qu'a la redirection du frontend:
 * un client parlant directement a l'API gardait un acces complet.
 *
 * <p>Pose dans la chaine Spring Security (et non comme filtre servlet global):
 * il lui faut un SecurityContext deja alimente pour connaitre l'utilisateur.
 */
@RequiredArgsConstructor
public class PasswordExpirationFilter extends OncePerRequestFilter {

    /** Ce qu'il faut pouvoir appeler pour sortir de l'expiration. */
    private static final Set<String> ALLOWED_PATHS = Set.of(
        "/api/auth/change-password",
        "/api/auth/logout",
        "/api/auth/me"
    );

    private final AuthService authService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean expired = authService.getCurrentUser()
            .map(userService::isPasswordExpired)
            .orElse(false);

        if (expired) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Password expired\",\"passwordExpired\":true}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || ALLOWED_PATHS.contains(path);
    }
}
