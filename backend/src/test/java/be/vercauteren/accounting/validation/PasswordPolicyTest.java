package be.vercauteren.accounting.validation;

import static org.assertj.core.api.Assertions.assertThat;

import be.vercauteren.accounting.dto.ChangePasswordRequest;
import be.vercauteren.accounting.dto.UserRequest;
import be.vercauteren.accounting.entity.UserRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Une contrainte composee qui ne se declenche pas est pire que pas de contrainte
 * du tout: elle donne l'apparence d'une regle. Ces tests verifient qu'elle est
 * bien lue sur les deux DTO, la creation de compte comme le changement.
 */
class PasswordPolicyTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static UserRequest userWithPassword(String password) {
        return new UserRequest("someone", "someone@example.com", password, UserRole.USER, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "abc123",           // l'ancienne limite: six caracteres passaient
        "short1!",          // sept caracteres
        "alllowercase1!",   // pas de majuscule
        "ALLUPPERCASE1!",   // pas de minuscule
        "NoDigitsHere!",    // pas de chiffre
        "NoSpecialChar1"    // pas de caractere special
    })
    void rejectsPasswordsBelowThePolicy(String password) {
        assertThat(validator.validate(userWithPassword(password))).isNotEmpty();
        assertThat(PasswordPolicy.isValid(password)).isFalse();
    }

    @Test
    void acceptsAPasswordMeetingThePolicy() {
        assertThat(validator.validate(userWithPassword("Str0ng&Pass"))).isEmpty();
        assertThat(PasswordPolicy.isValid("Str0ng&Pass")).isTrue();
    }

    @Test
    void appliesTheSamePolicyToPasswordChanges() {
        assertThat(validator.validate(new ChangePasswordRequest("old", "abc123"))).isNotEmpty();
        assertThat(validator.validate(new ChangePasswordRequest("old", "Str0ng&Pass"))).isEmpty();
    }

    @Test
    void rejectsPasswordsOverTheMaximumLength() {
        String tooLong = "A1!" + "a".repeat(PasswordPolicy.MAX_LENGTH);
        assertThat(validator.validate(userWithPassword(tooLong))).isNotEmpty();
        assertThat(PasswordPolicy.isValid(tooLong)).isFalse();
    }

    @Test
    void treatsANullPasswordAsInvalid() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
    }
}
