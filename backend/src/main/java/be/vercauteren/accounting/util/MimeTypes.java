package be.vercauteren.accounting.util;

import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Types MIME des documents acceptes, deduits de l'extension. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MimeTypes {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
        Map.entry("pdf", "application/pdf"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("tiff", "image/tiff"),
        Map.entry("tif", "image/tiff"),
        Map.entry("webp", "image/webp")
    );

    /** Extension en minuscules, sans le point, ou null si le nom n'en porte pas. */
    public static String extensionOf(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Type MIME du fichier, ou null si l'extension n'est pas prise en charge. */
    public static String forFileName(String fileName) {
        return BY_EXTENSION.get(extensionOf(fileName));
    }

    public static boolean isSupported(String fileName) {
        return forFileName(fileName) != null;
    }
}
