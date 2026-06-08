package tn.esprit.eventservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.eventservice.client.RecommendationMlClient;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionRequestDto;
import tn.esprit.eventservice.dto.ml.MlSentimentPredictionResponseDto;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PythonSentimentServiceTest {

    @Mock
    RecommendationMlClient recommendationMlClient;

    @InjectMocks
    PythonSentimentService service;

    @Test
    @DisplayName("predictSentiment() delegates to ML client and returns sentiment response")
    void predictSentiment_success() {
        MlSentimentPredictionResponseDto expected = new MlSentimentPredictionResponseDto(
                "This event was excellent and very useful.",
                "positive",
                Map.of("positive", 0.25, "neutral", -0.11, "negative", -1.24)
        );
        when(recommendationMlClient.predictSentiment(any(MlSentimentPredictionRequestDto.class)))
                .thenReturn(expected);

        MlSentimentPredictionResponseDto result =
                service.predictSentiment("  This event was excellent and very useful.  ");

        assertThat(result.getLabel()).isEqualTo("positive");
        assertThat(result.getComment()).isEqualTo("This event was excellent and very useful.");
        verify(recommendationMlClient).predictSentiment(
                new MlSentimentPredictionRequestDto("This event was excellent and very useful.")
        );
    }

    @Test
    @DisplayName("predictSentiment() rejects blank comments")
    void predictSentiment_blankComment_throws() {
        assertThatThrownBy(() -> service.predictSentiment("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Comment must not be blank");
    }
}
