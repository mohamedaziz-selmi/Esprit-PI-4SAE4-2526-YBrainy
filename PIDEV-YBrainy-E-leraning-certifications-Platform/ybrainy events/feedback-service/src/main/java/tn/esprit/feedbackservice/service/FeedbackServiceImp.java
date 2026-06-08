package tn.esprit.feedbackservice.service;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.feedbackservice.client.EventClient;
import tn.esprit.feedbackservice.client.InscriptionClient;
import tn.esprit.feedbackservice.dto.EventDto;
import tn.esprit.feedbackservice.dto.EventStatsDto;
import tn.esprit.feedbackservice.dto.FeedbackRequestDto;
import tn.esprit.feedbackservice.entity.Feedback;
import tn.esprit.feedbackservice.entity.FeedbackStatut;
import tn.esprit.feedbackservice.repository.FeedbackRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class FeedbackServiceImp implements IFeedbackService {

    private final FeedbackRepository feedbackRepository;

    /** Feign → inscription-service : student must have CONFIRMEE inscription */
    private final InscriptionClient inscriptionClient;

    /** Feign → event-service : event must be TERMINE */
    private final EventClient eventClient;

    // ─────────────────────────────────────────────────────────────────────────
    // Submit
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Feedback submitFeedback(FeedbackRequestDto dto) {
        validateRating(dto.rating());
        validateComment(dto.comment());

        // ── Rule 1 : event must exist and be TERMINE ──────────────────────────
        EventDto event = eventClient.findById(dto.eventId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found."
                ));

        if (!"TERMINE".equals(event.statut())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Feedback can only be submitted after the event has ended (statut must be TERMINE)."
            );
        }

        // ── Rule 2 : student must have a CONFIRMEE inscription ────────────────
        if (!inscriptionClient.hasConfirmedInscription(dto.studentId(), dto.eventId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only submit feedback for events you attended (CONFIRMEE inscription required)."
            );
        }

        // ── Rule 3 : one feedback per student per event ───────────────────────
        if (feedbackRepository.existsByStudentIdAndEventId(dto.studentId(), dto.eventId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already submitted a feedback for this event."
            );
        }

        Feedback feedback = new Feedback();
        feedback.setStudentId(dto.studentId());
        feedback.setEventId(dto.eventId());
        feedback.setRating(dto.rating());
        feedback.setComment(dto.comment());
        feedback.setSentimentLabel(normalizeSentimentLabel(dto.sentimentLabel()));
        feedback.setDateCreation(LocalDateTime.now());
        feedback.setStatut(FeedbackStatut.PUBLIE);

        return feedbackRepository.save(feedback);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Feedback updateFeedback(long idFeedback, FeedbackRequestDto dto) {
        validateRating(dto.rating());
        validateComment(dto.comment());

        Feedback existing = findOrThrow(idFeedback);

        // Ownership check: only the original student can update
        if (existing.getStudentId() != dto.studentId()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only update your own feedback."
            );
        }

        existing.setRating(dto.rating());
        existing.setComment(dto.comment());
        existing.setSentimentLabel(normalizeSentimentLabel(dto.sentimentLabel()));
        return feedbackRepository.save(existing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void deleteFeedback(long idFeedback) {
        findOrThrow(idFeedback);
        feedbackRepository.deleteById(idFeedback);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<Feedback> getFeedbacksByEvent(long eventId) {
        // Public view: only PUBLIE feedbacks
        return feedbackRepository.findByEventIdAndStatutOrderByDateCreationDesc(
                eventId, FeedbackStatut.PUBLIE
        );
    }

    @Override
    public List<Feedback> getAllFeedbacksByEvent(long eventId) {
        // Admin view: all feedbacks regardless of statut
        return feedbackRepository.findByEventIdOrderByDateCreationDesc(eventId);
    }

    @Override
    public List<Feedback> getFeedbacksByStudent(long studentId) {
        return feedbackRepository.findByStudentIdOrderByDateCreationDesc(studentId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public EventStatsDto getStatsByEvent(long eventId) {
        Double avg = feedbackRepository.findAverageRatingByEventId(eventId);
        double averageRating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;

        // Build rating distribution map  { 1→count, 2→count, 3→count, 4→count, 5→count }
        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0L);

        List<Object[]> rawDistribution = feedbackRepository.findRatingDistributionByEventId(eventId);
        for (Object[] row : rawDistribution) {
            int    rating = ((Number) row[0]).intValue();
            long   count  = ((Number) row[1]).longValue();
            distribution.put(rating, count);
        }

        long totalFeedbacks = distribution.values().stream().mapToLong(Long::longValue).sum();

        return new EventStatsDto(eventId, averageRating, totalFeedbacks, distribution);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin moderation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Feedback maskFeedback(long idFeedback) {
        Feedback feedback = findOrThrow(idFeedback);
        feedback.setStatut(FeedbackStatut.MASQUE);
        return feedbackRepository.save(feedback);
    }

    @Override
    public Feedback unmaskFeedback(long idFeedback) {
        Feedback feedback = findOrThrow(idFeedback);
        feedback.setStatut(FeedbackStatut.PUBLIE);
        return feedbackRepository.save(feedback);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Feedback findOrThrow(long idFeedback) {
        return feedbackRepository.findById(idFeedback)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Feedback not found."
                ));
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5."
            );
        }
    }

    private void validateComment(String comment) {
        if (comment != null && comment.length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Comment must not exceed 1000 characters."
            );
        }
    }

    private String normalizeSentimentLabel(String sentimentLabel) {
        if (sentimentLabel == null) {
            return null;
        }

        String normalized = sentimentLabel.trim().toLowerCase();
        if (normalized.isBlank()) {
            return null;
        }

        return switch (normalized) {
            case "positive", "neutral", "negative" -> normalized;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sentimentLabel must be one of: positive, neutral, negative."
            );
        };
    }
}
