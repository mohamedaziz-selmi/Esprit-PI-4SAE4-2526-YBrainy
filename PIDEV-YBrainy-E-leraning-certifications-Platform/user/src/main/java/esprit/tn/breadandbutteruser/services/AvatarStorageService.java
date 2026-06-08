package esprit.tn.breadandbutteruser.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AvatarStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${app.upload.images-dir:C:/xampp/htdocs/images}")
    private String imagesDir;

    @Value("${app.upload.images-public-base-url:http://localhost/images}")
    private String imagesPublicBaseUrl;

    public StoredAvatar storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 5 MB or smaller");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }

        String extension = resolveExtension(file);
        Path imagesRoot = Paths.get(imagesDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(imagesRoot);

            String fileName = buildFileName(extension);
            Path target = imagesRoot.resolve(fileName).normalize();
            if (!target.startsWith(imagesRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String publicUrl = normalizePublicBase(imagesPublicBaseUrl) + "/" + fileName;
            log.info("Stored avatar image at {} -> {}", target, publicUrl);
            return new StoredAvatar(fileName, publicUrl, file.getSize());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to store avatar image in {}: {}", imagesRoot, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store image");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(originalFilename);
        String normalized = ext != null ? ext.trim().toLowerCase(Locale.ROOT) : "";

        if (ALLOWED_EXTENSIONS.contains(normalized)) {
            return "jpeg".equals(normalized) ? "jpg" : normalized;
        }

        String contentType = String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
        if (contentType.contains("png")) return "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
        if (contentType.contains("gif")) return "gif";
        if (contentType.contains("webp")) return "webp";

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type");
    }

    private String buildFileName(String extension) {
        String ts = LocalDateTime.now().format(TS_FORMAT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "avatar_" + ts + "_" + suffix + "." + extension;
    }

    private String normalizePublicBase(String base) {
        String trimmed = StringUtils.hasText(base) ? base.trim() : "http://localhost/images";
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record StoredAvatar(String fileName, String publicUrl, long sizeBytes) {}
}
