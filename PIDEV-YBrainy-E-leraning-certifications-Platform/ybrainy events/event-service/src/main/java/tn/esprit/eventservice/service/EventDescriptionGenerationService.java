package tn.esprit.eventservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.esprit.eventservice.entity.EventType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
public class EventDescriptionGenerationService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ybrainy.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ybrainy.ai.groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    public EventDescriptionGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public GeneratedDescriptionResult generateDescription(String eventName, String rawType) {
        String sanitizedName = eventName == null ? "" : eventName.trim();
        if (sanitizedName.isBlank()) {
            return new GeneratedDescriptionResult("", false);
        }

        EventType eventType = parseType(rawType);
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return new GeneratedDescriptionResult(buildFallbackDescription(sanitizedName, eventType), false);
        }

        try {
            String aiDescription = requestGroqDescription(sanitizedName, eventType);
            if (aiDescription != null && !aiDescription.isBlank()) {
                log.info("Groq description generated successfully for eventName={}", sanitizedName);
                return new GeneratedDescriptionResult(aiDescription, true);
            }
        } catch (Exception exception) {
            log.warn("Groq description generation failed for eventName={}: {}", sanitizedName, exception.getMessage(), exception);
        }

        return new GeneratedDescriptionResult(buildFallbackDescription(sanitizedName, eventType), false);
    }

    private String requestGroqDescription(String eventName, EventType eventType)
            throws IOException, InterruptedException {
        String prompt = buildPrompt(eventName, eventType);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", groqModel);
        ArrayNode messages = root.putArray("messages");
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", "You write concise, polished event descriptions for the YBrainy platform."));
        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", prompt));
        root.put("temperature", 0.8);
        root.put("max_tokens", 180);
        String payload = objectMapper.writeValueAsString(root);

        String endpoint = "https://api.groq.com/openai/v1/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Groq HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode jsonRoot = objectMapper.readTree(response.body());
        JsonNode textNode = jsonRoot.path("choices").path(0).path("message").path("content");
        String text = textNode.isMissingNode() ? "" : textNode.asText("");
        return sanitizeGeneratedText(text);
    }

    private String buildPrompt(String eventName, EventType eventType) {
        String typeLabel = eventType == null ? "professional event" : eventType.name().toLowerCase(Locale.ROOT);
        return "You are writing for the YBrainy backoffice event form. "
                + "Create one polished description paragraph for an event named \"" + eventName + "\". "
                + "The event type is " + typeLabel + ". "
                + "Keep it professional, modern, and clear. "
                + "Return only one paragraph between 55 and 95 words. "
                + "No bullet points, no headings, no markdown, no emojis, and no quotation marks.";
    }

    private String sanitizeGeneratedText(String text) {
        String sanitized = text == null ? "" : text.trim();
        sanitized = sanitized.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
        if (sanitized.length() > 5000) {
            sanitized = sanitized.substring(0, 4997).trim() + "...";
        }
        return sanitized;
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

    private String buildFallbackDescription(String name, EventType type) {
        String lower = name.toLowerCase(Locale.ROOT);
        String topic = "professional skills";
        if (type != null) {
            topic = switch (type) {
                case HACKATHON -> "innovation and rapid problem-solving";
                case WEBINAIRE -> "modern digital knowledge and expert-led insights";
                case FORMATION -> "professional growth and applied learning";
                case ATELIER -> "hands-on practice and collaborative skill-building";
            };
        }

        if (lower.contains("hack")) topic = "innovation and rapid problem-solving";
        else if (lower.contains("web")) topic = "modern web technologies";
        else if (lower.contains("data")) topic = "data analysis and decision-making";
        else if (lower.contains("ai") || lower.contains("ia")) topic = "AI fundamentals and practical use cases";
        else if (lower.contains("design")) topic = "creative design and user experience";
        else if (lower.contains("market")) topic = "digital marketing strategies";
        else if (lower.contains("cloud")) topic = "cloud platforms and deployment";

        String[] goals = {
                "build strong practical understanding",
                "develop real-world problem-solving habits",
                "improve collaboration and communication",
                "transform theory into hands-on outcomes",
                "gain clear, actionable methods"
        };
        String[] formats = {
                "interactive workshops",
                "guided activities",
                "expert demonstrations",
                "collaborative mini-projects",
                "practical case studies"
        };
        String[] outcomes = {
                "apply what they learn immediately",
                "produce a concrete result by the end of the session",
                "leave with a clear action plan",
                "strengthen confidence in real scenarios",
                "improve both technical and strategic thinking"
        };

        int hash = nameHash(name);
        String goal = goals[hash % goals.length];
        String format = formats[(hash + 1) % formats.length];
        String outcome = outcomes[(hash + 2) % outcomes.length];

        return name + " focuses on " + topic + " and helps participants " + goal + ". Through "
                + format + ", this event creates an engaging environment where attendees can practice, exchange ideas, and progress effectively. "
                + "By the end, participants should be able to " + outcome + ".";
    }

    private int nameHash(String text) {
        int hash = 0;
        for (int i = 0; i < text.length(); i += 1) {
            hash = (hash * 31 + text.charAt(i)) & Integer.MAX_VALUE;
        }
        return hash;
    }

    public record GeneratedDescriptionResult(
            String description,
            boolean generatedByAi
    ) {}
}