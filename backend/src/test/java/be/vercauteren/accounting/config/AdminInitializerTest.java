package be.vercauteren.accounting.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Le compte administrateur initial, cree au demarrage et seulement s'il le faut. */
class AdminInitializerTest {

    private UserRepository userRepository;
    private AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        initializer = new AdminInitializer(userRepository, new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(initializer, "adminUsername", "root");
        ReflectionTestUtils.setField(initializer, "adminEmail", "root@test.local");
    }

    private void password(String value) {
        ReflectionTestUtils.setField(initializer, "adminPassword", value);
    }

    @Test
    void withoutPasswordNothingIsCreated() {
        password(" ");
        initializer.run();
        password(null);
        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void anExistingAdminIsLeftAloneEvenWithAWeakPassword() {
        password("weak");
        when(userRepository.existsByUsername("root")).thenReturn(true);

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void aWeakPasswordDoesNotCreateTheAccount() {
        password("weak");

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAnEnabledAdminWithAHashedPassword() {
        password("Str0ng!Pass");

        initializer.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().getPassword()).startsWith("$2");
        assertThat(saved.getValue().getPasswordExpiresAt()).isAfter(saved.getValue().getPasswordChangedAt());
    }
}
