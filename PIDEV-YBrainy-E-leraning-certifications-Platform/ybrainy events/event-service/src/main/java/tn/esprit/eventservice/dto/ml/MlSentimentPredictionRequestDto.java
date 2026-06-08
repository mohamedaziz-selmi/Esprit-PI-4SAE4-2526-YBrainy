package tn.esprit.eventservice.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlSentimentPredictionRequestDto {
    private String comment;
}
