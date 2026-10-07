package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.vercauteren.accounting.util.InMemoryMultipartFile;
import java.io.IOException;
import java.util.Base64;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Conversion d'une photo de ticket en PDF d'une page. */
public class ImageToPdfServiceTest {

    /**
     * WebP 1x1 sans perte (VP8L) et avec perte (VP8): les deux encodages courants.
     * Publics pour le test d'API du depot, qui reprend le second.
     */
    public static final String LOSSLESS_WEBP = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";
    public static final String LOSSY_WEBP ="UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";

    private final ImageToPdfService service = new ImageToPdfService();

    private static InMemoryMultipartFile image(String contentType, byte[] content) {
        return new InMemoryMultipartFile("file", "photo", contentType, content);
    }

    @ParameterizedTest
    @ValueSource(strings = {LOSSLESS_WEBP, LOSSY_WEBP})
    void aWebpPhotoBecomesASinglePagePdf(String base64) throws IOException {
        byte[] pdf = service.convertToPdf(image("image/webp", Base64.getDecoder().decode(base64)));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void bytesNoReaderRecognisesAreRefused() {
        assertThatThrownBy(() -> service.convertToPdf(image("image/png", new byte[] {1, 2, 3, 4})))
            .isInstanceOf(IOException.class)
            .hasMessage("Unable to read image file");
    }

    @Test
    void onlyImageTypesAreConverted() {
        assertThat(service.isImage(image("image/webp", new byte[0]))).isTrue();
        assertThat(service.isImage(image("application/pdf", new byte[0]))).isFalse();
        assertThat(service.isImage(image(null, new byte[0]))).isFalse();
    }
}
