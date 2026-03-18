package be.vercauteren.accounting.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageToPdfService {

    private static final float A4_WIDTH = PDRectangle.A4.getWidth();
    private static final float A4_HEIGHT = PDRectangle.A4.getHeight();

    public byte[] convertToPdf(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IOException("Unable to read image file");
        }

        try (PDDocument document = new PDDocument()) {
            float imgWidth = image.getWidth();
            float imgHeight = image.getHeight();

            // Scale to fit within A4 while preserving aspect ratio
            float scale = Math.min(A4_WIDTH / imgWidth, A4_HEIGHT / imgHeight);
            float scaledWidth = imgWidth * scale;
            float scaledHeight = imgHeight * scale;

            PDPage page = new PDPage(new PDRectangle(scaledWidth, scaledHeight));
            document.addPage(page);

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, 0, 0, scaledWidth, scaledHeight);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    public boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }
}
