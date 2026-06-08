package tn.esprit.eventservice.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationRequestDto {

    @JsonProperty("studentId")
    private long studentId;

    private int limit;

    @JsonProperty("svdWeight")
    private double svdWeight;

    @JsonProperty("contentWeight")
    private double contentWeight;

    @JsonProperty("candidateEvents")
    private List<MlRecommendationCandidateDto> candidateEvents;

    private List<MlHistoryItemDto> history;
}
