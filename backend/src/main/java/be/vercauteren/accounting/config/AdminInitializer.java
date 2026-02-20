package be.vercauteren.accounting.config;

import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.repository.UserRepository;
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
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Admin user '{}' already exists, skipping creation", adminUsername);
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
