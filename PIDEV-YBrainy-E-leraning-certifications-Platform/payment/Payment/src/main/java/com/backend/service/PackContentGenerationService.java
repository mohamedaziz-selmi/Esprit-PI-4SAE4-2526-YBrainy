package com.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.pack.GeneratePackContentRequestDTO;
import com.backend.dto.pack.GeneratePackContentResponseDTO;
import com.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PackContentGenerationService {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final String DEFAULT_RAPIDAPI_HOST = "ai-content-writer.p.rapidapi.com";

    private static final List<String> DESCRIPTION_KEYS = List.of(
            "description", "content", "text", "result", "output", "article", "generated_text", "data"
    );
    private static final List<String> TITLE_KEYS = List.of(
            "title", "headline", "subject"
    );
    private static final List<String> MESSAGE_KEYS = List.of(
            "message", "detail", "status", "error"
    );

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${content.writer.api-url:https://ai-content-writer.p.rapidapi.com/data}")
    private String apiUrl;

    @Value("${content.writer.api-key:}")
    private String apiKey;

    @Value("${content.writer.api-host:ai-content-writer.p.rapidapi.com}")
    private String apiHost;

    public GeneratePackContentResponseDTO generateContent(GeneratePackContentRequestDTO request) {
        String prompt = buildPrompt(request);
        String providerBody = callProvider(prompt);

        String generatedDescription = extractDescription(providerBody);
        if (!isNotBlank(generatedDescription)) {
            throw new BusinessRuleException("Content writer API returned empty content.");
        }
        generatedDescription = limitLength(cleanText(generatedDescription), MAX_DESCRIPTION_LENGTH);

        String generatedTitle = extractTitle(providerBody);
        if (!isNotBlank(generatedTitle)) {
            generatedTitle = request.title();
        }
        generatedTitle = limitLength(cleanText(generatedTitle), MAX_TITLE_LENGTH);

        return new GeneratePackContentResponseDTO(
                isNotBlank(generatedTitle) ? generatedTitle : null,
                generatedDescription,
                extractProviderMessage(providerBody)
        );
    }

    private String buildPrompt(GeneratePackContentRequestDTO request) {
        String title = safeText(request.title(), "E-Learning Pack");
        String category = safeText(request.categoryName(), "General");
        String level = safeText(request.level(), "INTERMEDIATE");
        String certificate = safeText(request.certificateName(), "Optional");
        String duration = request.durationHours() != null ? request.durationHours() + " hours" : "not specified";
        String existingDescription = isNotBlank(request.description())
                ? request.description().trim()
                : "No existing description.";

        return """
                You are writing a professional product description for an e-learning platform.
                Write one persuasive paragraph (80-140 words), clear and sales-oriented.
                Keep it realistic, avoid fake promises, and mention practical learner outcomes.
                Product details:
                - Title: %s
                - Category: %s
                - Level: %s
                - Duration: %s
                - Certificate: %s
                Existing description context: %s
                """.formatted(title, category, level, duration, certificate, existingDescription);
    }

    private String callProvider(String prompt) {
        if (!isNotBlank(apiKey)) {
            throw new BusinessRuleException("Content writer API key is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        headers.set("x-rapidapi-key", apiKey);
        headers.set("x-rapidapi-host", resolveRapidApiHost());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("topic", prompt);
        formData.add("prompt", prompt);
        formData.add("text", prompt);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<String> response = restTemplate().exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            String body = response.getBody();
            if (!isNotBlank(body)) {
                throw new BusinessRuleException("Content writer API returned an empty response.");
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            String message = readApiMessage(ex.getResponseBodyAsString());
            throw new BusinessRuleException(
                    "Content writer API request failed (" + ex.getStatusCode().value() + "): " + message
            );
        } catch (ResourceAccessException ex) {
            throw new BusinessRuleException("Content writer API is currently unreachable.");
        }
    }

    private String extractDescription(String providerBody) {
        try {
            JsonNode root = objectMapper.readTree(providerBody);
            String fromKnownKeys = findFirstFieldValue(root, DESCRIPTION_KEYS);
            if (isNotBlank(fromKnownKeys)) {
                return fromKnownKeys;
            }
            return findFirstTextValue(root);
        } catch (Exception ignored) {
            return providerBody;
        }
    }

    private String extractTitle(String providerBody) {
        try {
            JsonNode root = objectMapper.readTree(providerBody);
            return findFirstFieldValue(root, TITLE_KEYS);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractProviderMessage(String providerBody) {
        try {
            JsonNode root = objectMapper.readTree(providerBody);
            String message = findFirstFieldValue(root, MESSAGE_KEYS);
            if (isNotBlank(message)) {
                return limitLength(cleanText(message), 300);
            }
        } catch (Exception ignored) {
        }
        return "Generated by AI Content Writer.";
    }

    private String readApiMessage(String responseBody) {
        if (!isNotBlank(responseBody)) {
            return "No details available.";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = findFirstFieldValue(root, MESSAGE_KEYS);
            return isNotBlank(message) ? message : "Unexpected provider response.";
        } catch (Exception ignored) {
            return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
        }
    }

    private String findFirstFieldValue(JsonNode node, List<String> keys) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isObject()) {
            for (String key : keys) {
                JsonNode direct = node.get(key);
                if (direct != null) {
                    String value = findFirstTextValue(direct);
                    if (isNotBlank(value)) {
                        return value;
                    }
                }
            }
            for (JsonNode child : node) {
                String nested = findFirstFieldValue(child, keys);
                if (isNotBlank(nested)) {
                    return nested;
                }
            }
            return null;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findFirstFieldValue(child, keys);
                if (isNotBlank(nested)) {
                    return nested;
                }
            }
        }

        return null;
    }

    private String findFirstTextValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isTextual() || node.isNumber()) {
            return node.asText();
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = findFirstTextValue(child);
                if (isNotBlank(nested)) {
                    return nested;
                }
            }
            return null;
        }

        if (node.isObject()) {
            for (JsonNode child : node) {
                String nested = findFirstTextValue(child);
                if (isNotBlank(nested)) {
                    return nested;
                }
            }
        }

        return null;
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    private String resolveRapidApiHost() {
        if (isNotBlank(apiHost)) {
            return apiHost.trim();
        }
        try {
            URI uri = URI.create(apiUrl);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_RAPIDAPI_HOST;
    }

    private String cleanText(String value) {
        if (!isNotBlank(value)) {
            return value;
        }
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\s*", "");
        cleaned = cleaned.replaceAll("\\s*```$", "");
        return cleaned.trim();
    }

    private String limitLength(String value, int limit) {
        if (!isNotBlank(value) || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit).trim();
    }

    private String safeText(String value, String fallback) {
        return isNotBlank(value) ? value.trim() : fallback;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
