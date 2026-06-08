package tn.esprit.eventservice.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationCandidateDto {

    @JsonProperty("eventId")
    private long eventId;

    private String name;
    private String type;
    private String description;
    private String location;

    @JsonProperty("dateDebut")
    private String dateDebut;

    @JsonProperty("averageRating")
    private Double averageRating;
}
