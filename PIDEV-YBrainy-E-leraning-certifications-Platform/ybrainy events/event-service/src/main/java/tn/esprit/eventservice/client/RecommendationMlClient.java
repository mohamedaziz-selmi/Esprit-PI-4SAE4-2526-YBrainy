package tn.esprit.eventservice.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.eventservice.config.RecommendationMlProperties;
import tn.esprit.eventservice.dto.ml.MlRecommendationRequestDto;
import tn.esprit.eventservice.dto.ml.MlRecommendationResponseDto;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionRequestDto;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionResponseDto;

@Component
public class RecommendationMlClient {

    private final RestTemplate restTemplate;
    private final RecommendationMlProperties properties;

    public RecommendationMlClient(
            RestTemplate recommendationMlRestTemplate,
            RecommendationMlProperties properties
    ) {
        this.restTemplate = recommendationMlRestTemplate;
        this.properties = properties;
    }

    public MlRecommendationResponseDto recommendTopN(MlRecommendationRequestDto request) {
        String url = properties.getBaseUrl() + "/api/ml/recommendations/top-n";
        HttpEntity<MlRecommendationRequestDto> entity = createJsonEntity(request);

        try {
            return restTemplate.postForObject(url, entity, MlRecommendationResponseDto.class);
        } catch (HttpStatusCodeException exception) {
            String responseBody = exception.getResponseBodyAsString();
            throw new IllegalStateException(
                    "Recommendation ML service call failed: HTTP "
                            + exception.getStatusCode().value()
                            + " - "
                            + responseBody,
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("Recommendation ML service call failed", exception);
        }
    }

    public MlSentimentPredictionResponseDto predictSentiment(MlSentimentPredictionRequestDto request) {
        String url = properties.getBaseUrl() + "/api/ml/sentiment/predict";
        HttpEntity<MlSentimentPredictionRequestDto> entity = createJsonEntity(request);

        try {
            return restTemplate.postForObject(url, entity, MlSentimentPredictionResponseDto.class);
        } catch (HttpStatusCodeException exception) {
            String responseBody = exception.getResponseBodyAsString();
            throw new IllegalStateException(
                    "Sentiment ML service call failed: HTTP "
                            + exception.getStatusCode().value()
                            + " - "
                            + responseBody,
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("Sentiment ML service call failed", exception);
        }
    }

    private <T> HttpEntity<T> createJsonEntity(T payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }
}
