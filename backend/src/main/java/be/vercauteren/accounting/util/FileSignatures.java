package be.vercauteren.accounting.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Type reel d'un document, deduit de ses premiers octets. Le type MIME et
 * l'extension annonces par le client ne sont que declaratifs: seul le contenu
 * fait foi.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileSignatures {

    /** Un PDF tolere du bruit avant son en-tete, les autres formats non. */
    private static final int PDF_SEARCH_WINDOW = 1024;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    /** Type MIME du fichier d'apres son contenu, ou null si non reconnu. */
    public static String detect(MultipartFile file) throws IOException {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(PDF_SEARCH_WINDOW);
        }
        return detect(header);
    }

    static String detect(byte[] header) {
        if (containsPdfMagic(header)) return "application/pdf";
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(header, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(header, 'G', 'I', 'F', '8', '7', 'a')
            || startsWith(header, 'G', 'I', 'F', '8', '9', 'a')) return "image/gif";
        if (startsWith(header, 'B', 'M')) return "image/bmp";
        if (startsWith(header, 'I', 'I', 0x2A, 0x00)
            || startsWith(header, 'M', 'M', 0x00, 0x2A)) return "image/tiff";
        if (startsWith(header, 'R', 'I', 'F', 'F') && matchesAt(header, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        return null;
    }

    private static boolean containsPdfMagic(byte[] header) {
        int limit = Math.min(header.length, PDF_SEARCH_WINDOW) - PDF_MAGIC.length;
        for (int i = 0; i <= limit; i++) {
            if (Arrays.equals(header, i, i + PDF_MAGIC.length, PDF_MAGIC, 0, PDF_MAGIC.length)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] header, int... expected) {
        return matchesAt(header, 0, expected);
    }

    private static boolean matchesAt(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != (expected[i] & 0xFF)) return false;
        }
        return true;
    }
}
