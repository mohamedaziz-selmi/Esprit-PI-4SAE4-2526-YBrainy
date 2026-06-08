package tn.esprit.eventservice.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationResponseDto {

    @JsonProperty("studentId")
    private long studentId;

    private int limit;

    @JsonProperty("usedHistoryCount")
    private int usedHistoryCount;

    private List<MlRecommendedEventDto> recommendations;
}
