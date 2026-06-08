package tn.esprit.tpfoyer.Controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/courses/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    /**
     * Validates that the resolved path is within the allowed upload directory.
     * Prevents path traversal attacks like '../../../etc/passwd'.
     */
    private Path validateAndResolvePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        
        // Normalize the path to remove any redundant elements like '..' or '.'
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolvedPath = basePath.resolve(relativePath).normalize();
        
        // Security check: ensure the resolved path is still within the base directory
        if (!resolvedPath.startsWith(basePath)) {
            log.warn("Path traversal attempt detected: {}", relativePath);
            return null;
        }
        
        return resolvedPath;
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Path file = validateAndResolvePath(filename);
        if (file == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Resource resource = new UrlResource(file.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            
            String contentType = determineContentType(filename);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> serveFileNested(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String prefix = "/api/courses/files/";
        int idx = requestUri.indexOf(prefix);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String relativePath = requestUri.substring(idx + prefix.length());
        
        Path file = validateAndResolvePath(relativePath);
        if (file == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String filename = Paths.get(relativePath).getFileName().toString();
            String contentType = determineContentType(filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{subfolder}/{filename:.+}")
    public ResponseEntity<Resource> serveFileInSubfolder(
            @PathVariable String subfolder,
            @PathVariable String filename) {
        
        // Build relative path and validate
        String relativePath = subfolder + "/" + filename;
        Path file = validateAndResolvePath(relativePath);
        if (file == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Resource resource = new UrlResource(file.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            
            String contentType = determineContentType(filename);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
