package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Entities.enums.LessonType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;


@Service
public class FileStorageServiceImpl implements IFileStorageService {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Override
    public String storeThumbnail(MultipartFile file) {
        validateFile(file);
        return storeFile(file, "thumbnails");
    }

    @Override
    public String storeLessonContent(MultipartFile file, LessonType type) {
        validateFile(file);
        String subFolder = switch (type) {
            case VIDEO_UPLOAD -> "lessons/videos";
            case IMAGE        -> "lessons/images";
            case PDF          -> "lessons/pdfs";
            default -> throw new IllegalArgumentException(
                    "File upload is not applicable for lesson type: " + type);
        };
        return storeFile(file, subFolder);
    }

    @Override
    public String storeCertificate(MultipartFile file) {
        validateFile(file);
        return storeFile(file, "certificates");
    }

    @Override
    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path fullPath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
            Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            System.err.println("[FileStorageServiceImpl] Could not delete file: " + relativePath + " → " + e.getMessage());
        }
    }

    private String storeFile(MultipartFile file, String subFolder) {
        try {
            Path targetDir = Paths.get(uploadDir, subFolder).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);
            String extension = extractExtension(file.getOriginalFilename());
            String uniqueFilename = UUID.randomUUID() + extension;
            Path targetPath = targetDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return subFolder + "/" + uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "";
    }
}