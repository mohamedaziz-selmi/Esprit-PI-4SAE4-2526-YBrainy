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
public class MlHistoryItemDto {

    @JsonProperty("eventId")
    private long eventId;

    private int rating;

    @JsonProperty("eventType")
    private String eventType;

    private String description;
}
