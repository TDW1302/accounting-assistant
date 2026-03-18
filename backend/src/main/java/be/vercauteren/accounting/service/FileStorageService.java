package be.vercauteren.accounting.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path uploadDirectory;

    public FileStorageService(@Value("${app.upload.directory}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory);
    }

    public String store(MultipartFile file, String filename, Integer year) throws IOException {
        Path yearDir = uploadDirectory.resolve(String.valueOf(year));
        Files.createDirectories(yearDir);

        Path target = yearDir.resolve(filename);
        if (!target.normalize().startsWith(uploadDirectory.normalize())) {
            throw new IOException("Invalid file path: path traversal detected");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return target.toString();
    }

    public void delete(String filePath) throws IOException {
        Path path = Path.of(filePath);
        Files.deleteIfExists(path);
    }
}
