package tn.esprit.feedbackservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class SpeechToTextService {

    private static final long MAX_AUDIO_SIZE_BYTES = 10L * 1024L * 1024L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String huggingFaceApiUrl;
    private final String huggingFaceToken;

    public SpeechToTextService(
            ObjectMapper objectMapper,
            @Value("${huggingface.api.url}") String huggingFaceApiUrl,
            @Value("${huggingface.api.token:}") String huggingFaceToken
    ) {
        this.objectMapper = objectMapper;
        this.huggingFaceApiUrl = huggingFaceApiUrl;
        this.huggingFaceToken = huggingFaceToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please provide an audio recording.");
        }
        if (audioFile.getSize() > MAX_AUDIO_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio recording must stay under 10 MB.");
        }
        if (!StringUtils.hasText(huggingFaceToken)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Speech transcription is not configured yet. Add the Hugging Face token on the server."
            );
        }

        try {
            String contentType = StringUtils.hasText(audioFile.getContentType())
                    ? audioFile.getContentType()
                    : "application/octet-stream";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(huggingFaceApiUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + huggingFaceToken)
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audioFile.getBytes()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Hugging Face transcription failed with status {} and body {}", response.statusCode(), response.body());
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Speech transcription failed. Please try again in a moment."
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = extractText(root);
            if (!StringUtils.hasText(text)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "No transcription text was returned by the speech service."
                );
            }
            return text.trim();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded audio could not be processed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Speech transcription was interrupted.");
        }
    }

    private String extractText(JsonNode root) {
        if (root == null || root.isNull()) {
            return "";
        }
        if (root.isTextual()) {
            return root.asText("");
        }
        if (root.hasNonNull("text")) {
            return root.get("text").asText("");
        }
        if (root.has("chunks") && root.get("chunks").isArray()) {
            StringBuilder transcript = new StringBuilder();
            for (JsonNode chunk : root.get("chunks")) {
                if (chunk.hasNonNull("text")) {
                    if (!transcript.isEmpty()) {
                        transcript.append(' ');
                    }
                    transcript.append(chunk.get("text").asText(""));
                }
            }
            return transcript.toString();
        }
        return "";
    }
}