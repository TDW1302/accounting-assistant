package be.vercauteren.accounting.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Contrainte composee portant {@link PasswordPolicy} sur un champ de DTO. */
@Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
@Pattern(regexp = PasswordPolicy.PATTERN)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Documented
// RECORD_COMPONENT pour les DTO, FIELD/METHOD parce que la propagation vers le
// champ et l'accesseur est ce que lit le validateur, ANNOTATION_TYPE pour rester
// composable a son tour.
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
         ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default PasswordPolicy.MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
