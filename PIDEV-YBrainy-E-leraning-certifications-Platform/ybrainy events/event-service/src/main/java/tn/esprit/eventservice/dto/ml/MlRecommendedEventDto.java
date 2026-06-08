package tn.esprit.eventservice.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendedEventDto {

    @JsonProperty("eventId")
    private long eventId;

    private String name;
    private String type;
    private String description;
    private String location;

    @JsonProperty("dateDebut")
    private LocalDateTime dateDebut;

    @JsonProperty("predictedRating")
    private double predictedRating;

    @JsonProperty("preferenceScore")
    private double preferenceScore;

    @JsonProperty("popularityScore")
    private double popularityScore;

    @JsonProperty("contentScore")
    private double contentScore;

    @JsonProperty("finalScore")
    private double finalScore;

    private String reason;
}
