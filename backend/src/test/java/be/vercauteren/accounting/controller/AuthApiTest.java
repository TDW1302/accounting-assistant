package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.dto.ChangePasswordRequest;
import be.vercauteren.accounting.dto.LoginRequest;
import be.vercauteren.accounting.entity.AiProvider;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Connexion par session, expiration du mot de passe et reglages du compte.
 * Les connexions passent par le vrai AuthenticationManager et le vrai BCrypt.
 */
class AuthApiTest extends IntegrationTest {

    /** Une adresse par test: le rate limiting du login compte par IP. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private User alice;
    private RequestPostProcessor fromOwnAddress;

    @BeforeEach
    void setUp() {
        alice = newUser("alice", UserRole.USER);
        String ip = "10.1.0." + NEXT_IP.getAndIncrement();
        fromOwnAddress = request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(fromOwnAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(username, password))))
            .andReturn();
    }

    @Test
    void aLoginOpensASessionThatIdentifiesTheUserUntilLogout() throws Exception {
        MvcResult result = login("alice", PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) read(result, "$.user.username")).isEqualTo("alice");
        assertThat((Boolean) read(result, "$.passwordExpired")).isFalse();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.aiProvider").value("CLAUDE"));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void aSecondLoginReplacesTheExistingSession() throws Exception {
        MockHttpSession previous = new MockHttpSession();

        MvcResult result = mvc.perform(post("/api/auth/login").with(fromOwnAddress).session(previous)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest("alice", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(previous.isInvalid()).isTrue();
        assertThat(result.getRequest().getSession(false)).isNotSameAs(previous);
    }

    @Test
    void wrongCredentialsAndDisabledAccountsAreRejectedAlike() throws Exception {
        assertThat(login("alice", "Wrong!Pass1").getResponse().getStatus()).isEqualTo(401);
        assertThat(login("nobody", PASSWORD).getResponse().getStatus()).isEqualTo(401);

        alice.setEnabled(false);
        userRepository.save(alice);
        MvcResult disabled = login("alice", PASSWORD);
        assertThat(disabled.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) read(disabled, "$.error")).isEqualTo("Invalid username or password");
    }

    @Test
    void anEmptyLoginIsInvalid() throws Exception {
        assertThat(login("", "").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void anExpiredPasswordOnlyLeavesTheWayToChangeIt() throws Exception {
        alice.setPasswordExpiresAt(LocalDateTime.now().minusDays(1));
        userRepository.save(alice);

        mvc.perform(get("/api/invoices").param("year", "2026").with(as(alice)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.passwordExpired").value(true));

        mvc.perform(get("/api/auth/me").with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passwordExpired").value(true));

        mvc.perform(post("/api/auth/change-password").with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ChangePasswordRequest(PASSWORD, "N3w!Password"))))
            .andExpect(status().isNoContent());

        User changed = userRepository.findByUsername("alice").orElseThrow();
        assertThat(passwordEncoder.matches("N3w!Password", changed.getPassword())).isTrue();
        assertThat(changed.getPasswordExpiresAt()).isAfter(LocalDateTime.now().plusMonths(2));
    }

    @Test
    void changingThePasswordRequiresTheCurrentOneAndAStrongNewOne() throws Exception {
        mvc.perform(post("/api/auth/change-password").with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ChangePasswordRequest("Not!theRight1", "N3w!Password"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Current password is incorrect"));

        mvc.perform(post("/api/auth/change-password").with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ChangePasswordRequest(PASSWORD, "weak"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void theAiProviderIsAPerUserPreference() throws Exception {
        mvc.perform(post("/api/auth/ai-provider").with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiProvider\":\"GEMINI\"}"))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("alice").orElseThrow().getAiProvider())
            .isEqualTo(AiProvider.GEMINI);
    }

    /**
     * Un principal qui n'est pas un compte de l'application — rien ne devrait en
     * produire, mais les endpoints de compte n'ont alors personne a qui repondre.
     */
    @Test
    void accountEndpointsAnswerUnauthorizedWithoutAnApplicationUser() throws Exception {
        RequestPostProcessor foreign = user("ghost").roles("USER");

        mvc.perform(get("/api/auth/me").with(foreign))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/change-password").with(foreign).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ChangePasswordRequest(PASSWORD, "N3w!Password"))))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/ai-provider").with(foreign).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiProvider\":\"CLAUDE\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCallsAreUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void theConfigReportsPeppolAndInboxErrors() throws Exception {
        mvc.perform(get("/api/config").with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.peppolEnabled").value(true))
            .andExpect(jsonPath("$.inboxErrorCount").value(0));
    }
}
