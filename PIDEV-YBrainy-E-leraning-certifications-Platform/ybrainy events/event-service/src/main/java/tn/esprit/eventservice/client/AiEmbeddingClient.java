package tn.esprit.eventservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Component
public class AiEmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(AiEmbeddingClient.class);
    
    @Value("${github.models.endpoint:https://models.github.ai/inference}")
    private String endpoint;
    
    @Value("${github.models.api-key:}")
    private String apiKey;
    
    @Value("${github.models.embedding.model:text-embedding-3-large}")
    private String modelName;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiEmbeddingClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Async method to fetch embedding for a text.
     * Caches the computation to avoid billing/latency for identical texts.
     */
    @Cacheable(value = "embeddings", key = "#p0")
    public List<Double> getEmbeddingSync(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("GitHub Models API key is missing. Skipping AI embedding.");
            return new ArrayList<>();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "input", List.of(text),
                    "model", modelName
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = endpoint.endsWith("/") ? endpoint + "embeddings" : endpoint + "/embeddings";

            String responseStr = restTemplate.postForObject(url, request, String.class);

            if (responseStr != null && !responseStr.isBlank()) {
                JsonNode response = objectMapper.readTree(responseStr);
                if (response.has("data") && response.get("data").isArray() && response.get("data").size() > 0) {
                    JsonNode embeddingArray = response.get("data").get(0).get("embedding");
                    List<Double> embedding = new ArrayList<>();
                    for (JsonNode val : embeddingArray) {
                        embedding.add(val.asDouble());
                    }
                    return embedding;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to fetch embedding from GitHub Models API: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
    
    public CompletableFuture<List<Double>> getEmbeddingAsync(String text) {
        return CompletableFuture.supplyAsync(() -> getEmbeddingSync(text));
    }
    
    /**
     * Computes Cosine Similarity between two embeddings.
     */
    public double computeCosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA == null || vecB == null || vecA.isEmpty() || vecB.isEmpty() || vecA.size() != vecB.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.size(); i++) {
            double a = vecA.get(i);
            double b = vecB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
