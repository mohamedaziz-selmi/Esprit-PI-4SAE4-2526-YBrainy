package tn.esprit.lessonservice.services;

import tn.esprit.lessonservice.entities.LessonType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements IFileStorageService {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Override
    public String storeLessonContent(MultipartFile file, LessonType type) {
        String subDir = switch (type) {
            case VIDEO_UPLOAD -> "lessons/videos";
            case PDF -> "lessons/pdfs";
            case IMAGE -> "lessons/images";
            default -> "lessons/misc";
        };
        return store(file, subDir);
    }

    @Override
    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path full = Paths.get(uploadDir).resolve(relativePath).normalize();
            Files.deleteIfExists(full);
        } catch (Exception e) {
            log.warn("[FileStorage] Could not delete {}: {}", relativePath, e.getMessage());
        }
    }

    private String store(MultipartFile file, String subDir) {
        try {
            Path dir = Paths.get(uploadDir).resolve(subDir);
            Files.createDirectories(dir);
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename),
                StandardCopyOption.REPLACE_EXISTING);
            return subDir + "/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
