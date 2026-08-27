package be.vercauteren.accounting.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Regle unique pour tous les mots de passe de l'application. La creation de
 * compte et le changement de mot de passe la partagent: elles divergeaient, et
 * un compte cree par un administrateur pouvait naitre avec six caracteres sans
 * contrainte de composition, puis vivre trois mois ainsi.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    /** Au moins une minuscule, une majuscule, un chiffre et un caractere special. */
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$";

    public static final String MESSAGE =
        "Password must be 8 to 128 characters and contain at least one lowercase, "
            + "one uppercase, one digit, and one special character";

    /** Meme regle, pour les mots de passe qui n'arrivent pas par un DTO valide. */
    public static boolean isValid(String password) {
        return password != null
            && password.length() >= MIN_LENGTH
            && password.length() <= MAX_LENGTH
            && password.matches(PATTERN);
    }
}
