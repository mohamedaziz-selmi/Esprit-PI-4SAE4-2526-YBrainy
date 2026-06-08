package tn.esprit.eventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatsDto {
    private long eventId;
    private double averageRating;
    private int totalReviews;
    private Map<Integer, Long> ratingDistribution;
}
