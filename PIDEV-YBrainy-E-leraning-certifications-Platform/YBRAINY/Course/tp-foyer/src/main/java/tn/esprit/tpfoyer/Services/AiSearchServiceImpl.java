package tn.esprit.tpfoyer.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprit.tpfoyer.Dto.AiSearchIntentDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSearchServiceImpl implements IAiSearchService {

    @Value("${openrouter.api.key}")
    private String openRouterKey;

    private final RestTemplate restTemplate;

    @Override
    public AiSearchIntentDTO extractSearchIntent(String query) {
        try {
            String prompt = """
                    You are a search assistant for an e-learning platform.
                    Extract search intent from this student query: "%s"

                    The platform has these categories: PROGRAMMING, DESIGN, \
                    BUSINESS, MARKETING, SCIENCE, LANGUAGE, MATH, MUSIC, PHOTOGRAPHY
                    And these levels: BEGINNER, INTERMEDIATE, ADVANCED

                    Respond ONLY with a valid JSON object, no markdown, no explanation:
                    {
                      "keywords": "comma separated key search terms",
                      "category": "CATEGORY or null",
                      "level": "LEVEL or null",
                      "explanation": "one sentence explaining what the student wants"
                    }
                    """.formatted(query);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openRouterKey);
            headers.set("HTTP-Referer", "http://localhost:4301");
            headers.set("X-Title", "YBrainy");

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "meta-llama/llama-3.1-8b-instruct:free");
            body.put("messages", List.of(message));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://openrouter.ai/api/v1/chat/completions",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map responseBody = response.getBody();
            log.debug("[AiSearch] Raw response: {}", responseBody);

            List choices = (List) responseBody.get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map messageObj = (Map) firstChoice.get("message");
            String content = (String) messageObj.get("content");
            log.debug("[AiSearch] Extracted content: {}", content);

            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("```json", "")
                                 .replaceAll("```", "")
                                 .trim();
            }

            ObjectMapper mapper = new ObjectMapper();
            AiSearchIntentDTO intent = mapper.readValue(content, AiSearchIntentDTO.class);
            log.info("[AiSearch] Parsed: keywords='{}' category={} level={}",
                    intent.getKeywords(), intent.getCategory(), intent.getLevel());
            return intent;

        } catch (Exception e) {
            log.error("[AiSearch] ERROR extracting intent: {}", e.getMessage());
            AiSearchIntentDTO fallback = new AiSearchIntentDTO();
            fallback.setKeywords(query);
            fallback.setCategory(null);
            fallback.setLevel(null);
            fallback.setExplanation("Search for: " + query);
            return fallback;
        }
    }
}
