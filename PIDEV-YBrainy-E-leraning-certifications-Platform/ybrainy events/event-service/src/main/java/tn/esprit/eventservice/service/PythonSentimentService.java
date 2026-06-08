package tn.esprit.eventservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.eventservice.client.RecommendationMlClient;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionRequestDto;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionResponseDto;

@Service
@AllArgsConstructor
public class PythonSentimentService {

    private final RecommendationMlClient recommendationMlClient;

    public MlSentimentPredictionResponseDto predictSentiment(String comment) {
        String cleanedComment = comment != null ? comment.trim() : "";
        if (cleanedComment.isBlank()) {
            throw new IllegalArgumentException("Comment must not be blank for sentiment analysis");
        }

        MlSentimentPredictionRequestDto request = new MlSentimentPredictionRequestDto(cleanedComment);
        MlSentimentPredictionResponseDto response = recommendationMlClient.predictSentiment(request);
        if (response == null) {
            throw new IllegalStateException("Sentiment ML service returned no response");
        }
        return response;
    }
}
