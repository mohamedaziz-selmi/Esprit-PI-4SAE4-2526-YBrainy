package com.esprit.messagingservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class FileUploadController {

    @Value("${upload.path:uploads/messages}")
    private String uploadPath;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadPath);
        Files.createDirectories(dir);

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = dir.resolve(filename);
        file.transferTo(target.toFile());

        String url = "/uploads/messages/" + filename;
        String mediaType = file.getContentType() != null && file.getContentType().startsWith("image") ? "IMAGE" : "FILE";
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : filename;
        return ResponseEntity.ok(Map.of("url", url, "type", mediaType, "name", originalName));
    }
}
