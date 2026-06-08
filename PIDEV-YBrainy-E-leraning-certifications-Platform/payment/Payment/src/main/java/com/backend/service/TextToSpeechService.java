package com.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.pack.PackResponseDTO;
import com.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TextToSpeechService {

    private static final String DEFAULT_RAPIDAPI_HOST = "text-to-speach-api.p.rapidapi.com";

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${tts.api.url}")
    private String apiUrl;

    @Value("${tts.api.key}")
    private String apiKey;

    public AudioResult synthesizePack(PackResponseDTO pack) {
        String speechText = buildPackSpeechText(pack);
        return synthesizeText(speechText);
    }

    public AudioResult synthesizeText(String text) {
        String sanitizedText = text == null ? "" : text.trim();
        if (sanitizedText.isEmpty()) {
            throw new BusinessRuleException("Text-to-speech text cannot be empty.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.ALL));
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", resolveRapidApiHost(apiUrl));

        Map<String, String> payload = new HashMap<>();
        payload.put("text", sanitizedText);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate().exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );
            return normalizeAudioResponse(response);
        } catch (HttpStatusCodeException ex) {
            String apiMessage = readApiMessage(ex.getResponseBodyAsByteArray());
            throw new BusinessRuleException(
                    "Text-to-speech API request failed (" + ex.getStatusCode().value() + "): " + apiMessage
            );
        } catch (ResourceAccessException ex) {
            throw new BusinessRuleException("Text-to-speech API is currently unreachable.");
        }
    }

    private AudioResult normalizeAudioResponse(ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new BusinessRuleException("Text-to-speech API returned empty audio.");
        }

        MediaType contentType = response.getHeaders().getContentType();
        if (isJsonContentType(contentType)) {
            return parseJsonAudioResponse(body);
        }

        String resolvedContentType = contentType != null
                ? contentType.toString()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return new AudioResult(resolvedContentType, body);
    }

    private AudioResult parseJsonAudioResponse(byte[] jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            String audioUrl = findFirstFieldValue(root, List.of("audioUrl", "audio_url", "url", "audioLink", "link"));
            if (isNotBlank(audioUrl)) {
                return downloadAudioFromUrl(audioUrl.trim());
            }

            String audioContent = findFirstFieldValue(root, List.of("audioBase64", "audio_base64", "audioContent", "audio", "data"));
            if (isNotBlank(audioContent)) {
                String value = audioContent.trim();
                if (looksLikeUrl(value)) {
                    return downloadAudioFromUrl(value);
                }
                return decodeBase64Audio(value);
            }

            String apiError = findFirstFieldValue(root, List.of("message", "error", "detail"));
            if (isNotBlank(apiError)) {
                throw new BusinessRuleException("Text-to-speech API error: " + apiError);
            }
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessRuleException("Failed to parse text-to-speech API response.");
        }

        throw new BusinessRuleException("Text-to-speech API returned JSON without audio content.");
    }

    private AudioResult downloadAudioFromUrl(String audioUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate().getForEntity(audioUrl, byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessRuleException("Text-to-speech API returned an empty audio file.");
            }
            MediaType contentType = response.getHeaders().getContentType();
            String resolvedContentType = contentType != null ? contentType.toString() : "audio/mpeg";
            return new AudioResult(resolvedContentType, body);
        } catch (HttpStatusCodeException ex) {
            throw new BusinessRuleException("Failed to fetch generated speech audio from provider.");
        } catch (ResourceAccessException ex) {
            throw new BusinessRuleException("Generated speech audio URL is unreachable.");
        }
    }

    private AudioResult decodeBase64Audio(String rawAudio) {
        String mimeType = "audio/mpeg";
        String payload = rawAudio;

        if (rawAudio.startsWith("data:")) {
            int commaIndex = rawAudio.indexOf(',');
            if (commaIndex > 5) {
                String metadata = rawAudio.substring(5, commaIndex);
                int separatorIndex = metadata.indexOf(';');
                if (separatorIndex > 0) {
                    mimeType = metadata.substring(0, separatorIndex);
                } else if (isNotBlank(metadata)) {
                    mimeType = metadata;
                }
                payload = rawAudio.substring(commaIndex + 1);
            }
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            if (decoded.length == 0) {
                throw new BusinessRuleException("Text-to-speech API returned empty base64 audio.");
            }
            return new AudioResult(mimeType, decoded);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Text-to-speech API returned invalid base64 audio.");
        }
    }

    private String buildPackSpeechText(PackResponseDTO pack) {
        if (pack == null) {
            throw new BusinessRuleException("Pack data is required for text-to-speech.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Pack title: ").append(safeText(pack.getTitle())).append(". ");

        if (isNotBlank(pack.getDescription())) {
            sb.append(pack.getDescription().trim()).append(". ");
        }

        if (pack.getLevel() != null) {
            String level = pack.getLevel().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            sb.append("Difficulty level: ").append(level).append(". ");
        }

        if (pack.getDurationHours() != null) {
            sb.append("Estimated duration: ").append(pack.getDurationHours()).append(" hours. ");
        }

        if (pack.getSalePrice() != null) {
            sb.append("Current price: ").append(pack.getSalePrice()).append(" dollars.");
        }

        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String findFirstFieldValue(JsonNode root, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            String value = findFieldValueRecursive(root, fieldName);
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String findFieldValueRecursive(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        JsonNode direct = node.get(fieldName);
        if (direct != null && direct.isValueNode()) {
            return direct.asText();
        }

        if (node.isObject()) {
            for (JsonNode child : node) {
                String nested = findFieldValueRecursive(child, fieldName);
                if (isNotBlank(nested)) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findFieldValueRecursive(child, fieldName);
                if (isNotBlank(nested)) {
                    return nested;
                }
            }
        }

        return null;
    }

    private String readApiMessage(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return "No details available.";
        }

        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String message = findFirstFieldValue(node, List.of("message", "error", "detail"));
            return isNotBlank(message) ? message : "Unexpected provider response.";
        } catch (Exception ex) {
            return "Unexpected provider response.";
        }
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private boolean isJsonContentType(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return MediaType.APPLICATION_JSON.includes(mediaType) || mediaType.getSubtype().contains("json");
    }

    private String safeText(String text) {
        return isNotBlank(text) ? text.trim() : "Learning pack";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    private String resolveRapidApiHost(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_RAPIDAPI_HOST;
    }

    public record AudioResult(String contentType, byte[] audioBytes) {
    }
}
