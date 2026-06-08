package tn.esprit.tpfoyer.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprit.tpfoyer.Dto.MlConversionDTO;
import tn.esprit.tpfoyer.Dto.MlQualityDTO;
import tn.esprit.tpfoyer.Dto.MlRecommendationsResponseDTO;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MLServiceClient {

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;

    public MLServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // DSO1 — Predict conversion probability for a student
    public MlConversionDTO predictConversion(
            double timeSpent, double completionRate,
            double quizScores, double videosWatched,
            double numLogins, double forumReads) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("timeSpentOnCourse", timeSpent);
            body.put("completionRate", completionRate);
            body.put("quizScores", quizScores);
            body.put("numberOfVideosWatched", videosWatched);
            body.put("numLogins", numLogins);
            body.put("forumReads", forumReads);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<MlConversionDTO> response =
                    restTemplate.exchange(
                            mlServiceUrl + "/predict/conversion",
                            HttpMethod.POST, request,
                            MlConversionDTO.class);
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[ML] Conversion prediction failed: " + e.getMessage());
            return new MlConversionDTO(0.5, "MEDIUM", 50.0);
        }
    }

    // DSO2 — Get course recommendations by category and level
    public MlRecommendationsResponseDTO getRecommendations(
            String category, String level, int topN, List<Long> excludeIds) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("category", category);
            body.put("level", level);
            body.put("topN", topN);
            body.put("excludeIds", excludeIds);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<MlRecommendationsResponseDTO> response =
                    restTemplate.exchange(
                            mlServiceUrl + "/recommend",
                            HttpMethod.POST, request,
                            MlRecommendationsResponseDTO.class);
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[ML] Recommendations failed: " + e.getMessage());
            return new MlRecommendationsResponseDTO(new ArrayList<>(), new HashMap<>());
        }
    }

    // DSO3 — Predict course quality
    public MlQualityDTO predictQuality(
            int numLectures, double contentDuration,
            int lessonTypeVariety, double pctVideoLessons,
            int numLessons, int certEncoded, int levelEncoded,
            double rating, int ratingCount) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("numLectures", numLectures);
            body.put("contentDuration", contentDuration);
            body.put("lessonTypeVariety", lessonTypeVariety);
            body.put("pctVideoLessons", pctVideoLessons);
            body.put("numLessons", numLessons);
            body.put("certEncoded", certEncoded);
            body.put("levelEncoded", levelEncoded);
            body.put("rating", rating);
            body.put("ratingCount", ratingCount);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<MlQualityDTO> response =
                    restTemplate.exchange(
                            mlServiceUrl + "/predict/quality",
                            HttpMethod.POST, request,
                            MlQualityDTO.class);
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[ML] Quality prediction failed: " + e.getMessage());
            return new MlQualityDTO("UNKNOWN", 0.0, 0.0, false, null, null, null);
        }
    }

    // DSO4 — Get demand forecast (raw Map so all Flask fields pass through)
    public Map<String, Object> getForecast(int steps) {
        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(
                            mlServiceUrl + "/forecast/demand?steps=" + steps,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[ML] Forecast failed: " + e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("forecastSteps", steps);
            fallback.put("predictedDemand", new ArrayList<>());
            fallback.put("confidenceIntervals", new ArrayList<>());
            return fallback;
        }
    }
}
