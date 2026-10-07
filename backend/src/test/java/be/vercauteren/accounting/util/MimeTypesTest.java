package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import org.junit.jupiter.api.Test;

class MimeTypesTest {

    @Test
    void theTypeComesFromTheExtensionWhateverItsCase() {
        assertThat(MimeTypes.forFileName("scan.PDF")).isEqualTo("application/pdf");
        assertThat(MimeTypes.forFileName("photo.jpeg")).isEqualTo("image/jpeg");
        assertThat(MimeTypes.forFileName("photo.tif")).isEqualTo("image/tiff");
        assertThat(MimeTypes.isSupported("a.webp")).isTrue();
    }

    @Test
    void namesWithoutUsableExtensionAreUnsupported() {
        assertThat(MimeTypes.extensionOf(null)).isNull();
        assertThat(MimeTypes.extensionOf("README")).isNull();
        assertThat(MimeTypes.extensionOf(".pdf")).isNull();
        assertThat(MimeTypes.isSupported("notes.txt")).isFalse();
        assertThat(MimeTypes.forFileName(null)).isNull();
    }

    @Test
    void anInMemoryFileExposesItsContent() throws Exception {
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1, 2});
        InMemoryMultipartFile empty = new InMemoryMultipartFile("file", "b.pdf", "application/pdf", new byte[0]);

        assertThat(file.getName()).isEqualTo("file");
        assertThat(file.getSize()).isEqualTo(2);
        assertThat(file.isEmpty()).isFalse();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(file.getInputStream().readAllBytes()).containsExactly(1, 2);
        assertThatThrownBy(() -> file.transferTo(new File("x")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
