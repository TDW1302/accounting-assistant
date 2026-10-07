package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.vercauteren.accounting.util.InMemoryMultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Le rangement sur disque, et le refus d'en sortir. */
class FileStorageServiceTest {

    @TempDir
    Path root;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(root.toString());
    }

    private static InMemoryMultipartFile file(String content) {
        return new InMemoryMultipartFile("file", "x.pdf", "application/pdf", content.getBytes());
    }

    @Test
    void storesUnderTheYearThenRenamesAndDeletes() throws IOException {
        String stored = storage.store(file("v1"), "001-A.pdf", 2026);
        assertThat(Path.of(stored)).isEqualTo(root.resolve("2026/001-A.pdf"));
        assertThat(Files.readString(Path.of(stored))).isEqualTo("v1");

        // Un second depot remplace le premier.
        storage.store(file("v2"), "001-A.pdf", 2026);
        assertThat(Files.readString(Path.of(stored))).isEqualTo("v2");

        String renamed = storage.rename(stored, "001.1-A.pdf", 2026);
        assertThat(Path.of(renamed)).exists();
        assertThat(Path.of(stored)).doesNotExist();

        storage.delete(renamed);
        assertThat(Path.of(renamed)).doesNotExist();
        // Supprimer ce qui n'existe plus n'est pas une erreur.
        storage.delete(renamed);
    }

    @Test
    void refusesANameThatLeavesTheUploadDirectory() throws IOException {
        assertThatThrownBy(() -> storage.store(file("x"), "../../evil.pdf", 2026))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("path traversal");

        String stored = storage.store(file("x"), "ok.pdf", 2026);
        assertThatThrownBy(() -> storage.rename(stored, "../../evil.pdf", 2026))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("path traversal");
        assertThat(Path.of(stored)).exists();
    }
}
