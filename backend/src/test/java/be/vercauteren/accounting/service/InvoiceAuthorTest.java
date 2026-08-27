package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.repository.InvoiceRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Une facture doit porter son auteur. L'import Excel est le seul chemin dispense,
 * et il ne passe pas par InvoiceService: tout ce qui passe ici doit donc etre
 * refuse a defaut d'auteur, plutot que d'inscrire un null comme auparavant.
 */
class InvoiceAuthorTest {

    private AuthService authService;
    private InvoiceRepository invoiceRepository;
    private PlatformTransactionManager transactionManager;
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        invoiceRepository = mock(InvoiceRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        invoiceService = new InvoiceService(
            invoiceRepository,
            mock(SupplierService.class),
            mock(FileNameGenerator.class),
            mock(FileStorageService.class),
            authService,
            mock(ImageToPdfService.class),
            transactionManager);
    }

    private static InvoiceRequest request() {
        return new InvoiceRequest(
            null, 2026, InvoiceType.PURCHASE, 1L, null, null, null,
            LocalDate.of(2026, 1, 1), null, false, null, DateScope.NONE,
            null, null, null, null);
    }

    @Test
    void refusesToCreateAnInvoiceWithoutAnAuthenticatedAuthor() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.create(request(), InvoiceSource.MANUAL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("without an author");

        // Le refus tombe avant toute transaction: rien n'est tente en base.
        verify(invoiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(transactionManager, never())
            .getTransaction(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesAnExplicitlyNullAuthor() {
        assertThatThrownBy(() -> invoiceService.create(request(), InvoiceSource.INBOX, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void refusesACreationWithoutASource() {
        assertThatThrownBy(() -> invoiceService.create(request(), null, new User()))
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * L'entite n'a volontairement pas de valeur par defaut pour source: une
     * origine oubliee doit echouer, pas etre devinee.
     */
    @Test
    void invoiceSourceHasNoSilentDefault() {
        assertThat(be.vercauteren.accounting.entity.Invoice.builder().build().getSource()).isNull();
    }
}
