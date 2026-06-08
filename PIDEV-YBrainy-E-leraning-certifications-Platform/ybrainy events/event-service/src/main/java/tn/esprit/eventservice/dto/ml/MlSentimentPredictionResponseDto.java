package tn.esprit.eventservice.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlSentimentPredictionResponseDto {
    private String comment;
    private String label;
    private Map<String, Double> scores = new LinkedHashMap<>();
}
