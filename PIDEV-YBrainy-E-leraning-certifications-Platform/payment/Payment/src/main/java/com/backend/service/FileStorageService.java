package com.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    // Base path for XAMPP htdocs assets
    // Ensuring we use forward slashes for cross-platform compatibility handling in
    // Java
    private final Path rootLocation = Paths.get("C:/xampp/htdocs/assets/img");

    public String save(MultipartFile file, String subDir) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file.");
        }

        // Create directory if not exists
        Path destinationDir = rootLocation.resolve(subDir);
        if (!Files.exists(destinationDir)) {
            Files.createDirectories(destinationDir);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;

        Path destinationFile = destinationDir.resolve(newFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return newFilename;
    }
}
