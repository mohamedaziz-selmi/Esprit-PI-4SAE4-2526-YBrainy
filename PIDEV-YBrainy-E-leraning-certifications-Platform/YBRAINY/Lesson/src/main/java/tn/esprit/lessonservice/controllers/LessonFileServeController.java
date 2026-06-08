package tn.esprit.lessonservice.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.nio.file.*;

@RestController
@Slf4j
public class LessonFileServeController {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @GetMapping("/api/courses/files/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String relativePath = uri.replaceFirst("^.*/api/courses/files/", "");

        if (relativePath.isBlank() || relativePath.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path base = Paths.get(uploadDir).normalize().toAbsolutePath();
            Path filePath = base.resolve(relativePath).normalize();

            // prevent path traversal
            if (!filePath.startsWith(base)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("[FileServe] Not found: {}", relativePath);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(guessContentType(relativePath)))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            log.error("[FileServe] Error serving {}: {}", relativePath, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private String guessContentType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".mp4"))  return "video/mp4";
        if (p.endsWith(".webm")) return "video/webm";
        if (p.endsWith(".ogg"))  return "video/ogg";
        if (p.endsWith(".pdf"))  return "application/pdf";
        if (p.endsWith(".png"))  return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif"))  return "image/gif";
        if (p.endsWith(".svg"))  return "image/svg+xml";
        return "application/octet-stream";
    }
}
