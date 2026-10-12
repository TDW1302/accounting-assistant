package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Les erreurs techniques ne doivent rien laisser fuir: message fixe, detail au
 * journal seulement.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void technicalFailuresAnswerAFixedMessage() {
        assertThat(handler.handleIO(new IOException("/secret/path")))
            .containsEntry("error", "A file operation failed");
        assertThat(handler.handleDataIntegrity(new DataIntegrityViolationException("uk_invoice violated")))
            .containsEntry("error", "Operation conflicts with existing data");
        assertThat(handler.handleUnexpected(new RuntimeException("stack details")))
            .containsEntry("error", "An internal error occurred");
    }
}
