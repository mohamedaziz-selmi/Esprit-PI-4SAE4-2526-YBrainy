package tn.esprit.eventservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.eventservice.entity.EventType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
public class EventImageGenerationService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ybrainy.ai.image.endpoint:https://router.huggingface.co/hf-inference/models/}")
    private String imageEndpoint;

    @Value("${ybrainy.ai.image.model:stabilityai/stable-diffusion-3-medium-diffusers}")
    private String imageModel;

    @Value("${ybrainy.ai.image.hf-token:${HF_TOKEN:}}")
    private String hfToken;

    @Value("${ybrainy.ai.image.public-base-url:http://localhost:8081/Event/generated-images}")
    private String imagePublicBaseUrl;

    @Value("${ybrainy.ai.image.storage-dir:generated-event-images}")
    private String imageStorageDir;

    public EventImageGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    public GeneratedImageResult generateImage(String eventName, String eventDescription, String rawType) {
        String sanitizedName = eventName == null ? "" : eventName.trim();
        String sanitizedDescription = eventDescription == null ? "" : eventDescription.trim();
        if (sanitizedName.isBlank()) {
            return new GeneratedImageResult("", false);
        }
        if (hfToken == null || hfToken.isBlank()) {
            log.warn("HF token is missing for image generation.");
            return new GeneratedImageResult("", false);
        }

        EventType eventType = parseType(rawType);
        String prompt = sanitizePromptText(buildPrompt(sanitizedName, sanitizedDescription, eventType));

        try {
            GeneratedImageFile generatedFile = requestHuggingFaceImage(prompt, sanitizedName, sanitizedDescription, rawType);
            String imageUrl = saveGeneratedImage(generatedFile);
            return new GeneratedImageResult(imageUrl, true);
        } catch (Exception exception) {
            log.warn("HF image generation failed for eventName={}: {}", sanitizedName, exception.getMessage(), exception);
            return new GeneratedImageResult("", false);
        }
    }

    public GeneratedImageResult uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Please choose an image to upload.");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new ResponseStatusException(BAD_REQUEST, "Only image files are allowed.");
        }

        long maxSizeBytes = 6L * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded image must stay under 6 MB.");
        }

        try {
            String imageUrl = saveGeneratedImage(new GeneratedImageFile(file.getBytes(), contentType, file.getOriginalFilename()));
            return new GeneratedImageResult(imageUrl, false);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded image could not be stored.", exception);
        }
    }

    public GeneratedImageFile loadGeneratedImage(String fileName) {
        String safeFileName = sanitizeStoredFileName(fileName);
        try {
            Path filePath = resolveStorageDirectory().resolve(safeFileName);
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                throw new ResponseStatusException(NOT_FOUND, "Generated image not found.");
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null || !contentType.startsWith("image/")) {
                contentType = safeFileName.endsWith(".jpg") || safeFileName.endsWith(".jpeg")
                        ? "image/jpeg"
                        : "image/png";
            }

            return new GeneratedImageFile(Files.readAllBytes(filePath), contentType, safeFileName);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(NOT_FOUND, "Generated image could not be loaded.", exception);
        }
    }

    private GeneratedImageFile requestHuggingFaceImage(String prompt, String name, String description, String rawType)
            throws IOException, InterruptedException {
        String base = imageEndpoint == null ? "" : imageEndpoint.trim();
        if (base.isBlank()) {
            throw new IOException("Hugging Face image endpoint is not configured.");
        }

        String endpoint = base.endsWith("/") ? base + imageModel : base + "/" + imageModel;

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("inputs", prompt);

        ObjectNode parameters = payload.putObject("parameters");
        parameters.put("width", 1024);
        parameters.put("height", 1024);
        parameters.put("num_inference_steps", 28);
        parameters.put("guidance_scale", 7.0);
        parameters.put("negative_prompt", "blurry, low quality, distorted, watermark, text, logo, extra fingers, bad anatomy");
        parameters.put("seed", Math.abs((name + "|" + description + "|" + rawType).hashCode()));

        ObjectNode options = payload.putObject("options");
        options.put("wait_for_model", true);
        options.put("use_cache", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + hfToken)
                .header("Content-Type", "application/json")
                .header("Accept", "image/png")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("HF HTTP " + response.statusCode() + " - " + errorBody);
        }

        String contentType = response.headers().firstValue("content-type").orElse("image/png");
        if (!contentType.startsWith("image/")) {
            String nonImageBody = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("HF returned non-image content: " + contentType + " - " + nonImageBody);
        }

        return new GeneratedImageFile(response.body(), contentType, null);
    }

    private String buildPrompt(String name, String description, EventType type) {
        String typeLabel = type == null ? "professional event" : type.name().toLowerCase(Locale.ROOT);
        String trimmedDescription = description.length() > 220 ? description.substring(0, 220).trim() : description;
        return "A polished, modern promotional image for a YBrainy " + typeLabel + " event named " + name + ". "
                + "Professional, premium, realistic, visually clean, educational technology atmosphere, no text, no watermark. "
                + "Context: " + trimmedDescription;
    }

    private String sanitizePromptText(String value) {
        String sanitized = value == null ? "" : value;
        sanitized = sanitized.replaceAll("[\\r\\n\\t]+", " ");
        sanitized = sanitized.replaceAll("[\"'`]+", "");
        sanitized = sanitized.replaceAll("\\s{2,}", " ").trim();
        return sanitized.length() > 320 ? sanitized.substring(0, 320).trim() : sanitized;
    }

    private EventType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        try {
            return EventType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String saveGeneratedImage(GeneratedImageFile generatedFile) throws IOException {
        Path storageDir = resolveStorageDirectory();
        Files.createDirectories(storageDir);

        String extension = extensionForContentType(generatedFile.contentType());
        String fileName = UUID.randomUUID() + extension;
        Path filePath = storageDir.resolve(fileName);
        Files.write(filePath, generatedFile.bytes());

        String publicBase = imagePublicBaseUrl == null ? "" : imagePublicBaseUrl.trim();
        if (publicBase.endsWith("/")) {
            publicBase = publicBase.substring(0, publicBase.length() - 1);
        }

        return publicBase + "/" + fileName;
    }

    private Path resolveStorageDirectory() {
        Path configuredPath = Paths.get(imageStorageDir == null || imageStorageDir.isBlank()
                ? "generated-event-images"
                : imageStorageDir.trim());
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(configuredPath).normalize();
    }

    private String extensionForContentType(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private String sanitizeStoredFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "Generated image not found.");
        }

        String sanitized = Paths.get(fileName).getFileName().toString();
        if (sanitized.contains("..")) {
            throw new ResponseStatusException(NOT_FOUND, "Generated image not found.");
        }
        return sanitized;
    }

    public record GeneratedImageResult(
            String imageUrl,
            boolean generatedByAi
    ) {}

    public record GeneratedImageFile(
            byte[] bytes,
            String contentType,
            String fileName
    ) {}
}