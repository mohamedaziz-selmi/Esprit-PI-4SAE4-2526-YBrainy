package tn.esprit.feedbackservice.dto;

import java.util.Map;

/**
 * Aggregated statistics for a given event's feedbacks.
 * Returned by GET /Feedback/event/{eventId}/stats
 *
 * Example:
 * {
 *   "eventId": 3,
 *   "averageRating": 4.2,
 *   "totalFeedbacks": 15,
 *   "ratingDistribution": { 1: 0, 2: 1, 3: 2, 4: 5, 5: 7 }
 * }
 */
public record EventStatsDto(
        long             eventId,
        double           averageRating,
        long             totalFeedbacks,
        Map<Integer, Long> ratingDistribution   // key = 1..5, value = count
) {}
