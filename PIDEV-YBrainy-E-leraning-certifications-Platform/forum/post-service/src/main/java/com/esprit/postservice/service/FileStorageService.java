package com.esprit.postservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    private static final String SUBDIR = "posts";

    public String store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID().toString() + ext;

        Path dir = Paths.get(uploadDir, SUBDIR);
        Files.createDirectories(dir);

        Path dest = dir.resolve(filename);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored post file: {}", dest.toAbsolutePath());

        return "/uploads/posts/" + filename;
    }
}
