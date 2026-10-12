package be.vercauteren.accounting.config;

import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.repository.UserRepository;
import be.vercauteren.accounting.validation.PasswordPolicy;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD is not set — skipping admin user creation. Set ADMIN_PASSWORD to create an admin account.");
            return;
        }

        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Admin user '{}' already exists, skipping creation", adminUsername);
            return;
        }

        // Controle place apres l'existence du compte: une installation deja en
        // service ne doit pas se voir refuser un demarrage a cause d'un mot de
        // passe faible qui n'est de toute facon plus utilise.
        if (!PasswordPolicy.isValid(adminPassword)) {
            log.error("ADMIN_PASSWORD does not meet the password policy — admin user '{}' was NOT created. {}",
                adminUsername, PasswordPolicy.MESSAGE);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        User admin = User.builder()
            .username(adminUsername)
            .email(adminEmail)
            .password(passwordEncoder.encode(adminPassword))
            .role(UserRole.ADMIN)
            .enabled(true)
            .passwordChangedAt(now)
            .passwordExpiresAt(now.plusMonths(3))
            .createdAt(now)
            .build();

        userRepository.save(admin);
        log.info("Admin user '{}' created successfully", adminUsername);
    }
}
