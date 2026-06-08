package tn.esprit.feedbackservice.dto;

/**
 * Payload sent by the student to submit a feedback.
 */
public record FeedbackRequestDto(
        long   studentId,
        long   eventId,
        int    rating,    // 1 to 5
        String comment,   // optional, max 1000 chars
        String sentimentLabel
) {}
