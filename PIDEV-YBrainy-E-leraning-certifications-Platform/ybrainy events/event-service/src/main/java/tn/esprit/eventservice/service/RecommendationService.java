package tn.esprit.eventservice.service;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.eventservice.client.AiEmbeddingClient;
import tn.esprit.eventservice.client.FeedbackClient;
import tn.esprit.eventservice.client.InscriptionClient;
import tn.esprit.eventservice.dto.EventStatsDto;
import tn.esprit.eventservice.dto.FeedbackDto;
import tn.esprit.eventservice.dto.RecommendedEventDto;
import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.entity.EventStatut;
import tn.esprit.eventservice.entity.EventType;
import tn.esprit.eventservice.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);

    private final EventRepository eventRepository;
    private final InscriptionClient inscriptionClient;
    private final FeedbackClient feedbackClient;
    private final AiEmbeddingClient aiEmbeddingClient;

    public List<RecommendedEventDto> getRecommendationsForStudent(long studentId, int limit) {
        List<Event> allEvents = (List<Event>) eventRepository.findAll();
        List<Long> enrolledEventIds = safeGetEnrolledEventIds(studentId);

        LocalDateTime now = LocalDateTime.now();
        List<Event> candidateEvents = allEvents.stream()
                .filter(e -> isPrimaryCandidate(e, enrolledEventIds, now))
                .collect(Collectors.toList());

        if (candidateEvents.isEmpty()) {
            candidateEvents = allEvents.stream()
                    .filter(e -> isRelaxedCandidate(e, enrolledEventIds))
                    .sorted(Comparator.comparing(this::resolveEventTimelineAnchor))
                    .collect(Collectors.toList());
        }

        if (candidateEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<FeedbackDto> studentFeedbacks = safeGetStudentFeedbacks(studentId);

        Set<EventType> preferredTypes = new HashSet<>();
        List<String> positiveHistoryDescriptions = new ArrayList<>();

        for (long eventId : enrolledEventIds) {
            eventRepository.findById(eventId).ifPresent(e -> {
                preferredTypes.add(e.getType());
                if (e.getDescription() != null && !e.getDescription().isBlank()) {
                    positiveHistoryDescriptions.add(e.getDescription());
                }
            });
        }

        for (FeedbackDto feedback : studentFeedbacks) {
            if (feedback.getRating() >= 4) {
                eventRepository.findById(feedback.getEventId()).ifPresent(e -> {
                    preferredTypes.add(e.getType());
                    if (e.getDescription() != null
                            && !e.getDescription().isBlank()
                            && !positiveHistoryDescriptions.contains(e.getDescription())) {
                        positiveHistoryDescriptions.add(e.getDescription());
                    }
                });
            }
        }

        boolean isColdStart = preferredTypes.isEmpty() && positiveHistoryDescriptions.isEmpty();
        if (isColdStart) {
            return fallbackTopRatedEvents(candidateEvents, limit);
        }

        List<Double> profileEmbedding = computeProfileEmbedding(positiveHistoryDescriptions);
        List<RecommendedEventDto> scoredCandidates = new ArrayList<>();

        for (Event candidate : candidateEvents) {
            double collabScore = preferredTypes.contains(candidate.getType()) ? 1.0 : 0.0;

            EventStatsDto stats = safeGetStats(candidate.getIdEvent());
            double contentScore = 0.0;
            if (stats != null && stats.getAverageRating() > 0) {
                contentScore = stats.getAverageRating() / 5.0;
            }

            double aiScore = 0.0;
            if (profileEmbedding != null && !profileEmbedding.isEmpty() && candidate.getDescription() != null) {
                List<Double> candidateEmbedding = aiEmbeddingClient.getEmbeddingSync(candidate.getDescription());
                aiScore = aiEmbeddingClient.computeCosineSimilarity(profileEmbedding, candidateEmbedding);
                aiScore = Math.max(0.0, Math.min(1.0, aiScore));
            }

            double finalScore = (collabScore * 0.3) + (contentScore * 0.3) + (aiScore * 0.4);
            String reason = generateReason(collabScore, contentScore, aiScore, candidate.getType());

            scoredCandidates.add(RecommendedEventDto.builder()
                    .idEvent(candidate.getIdEvent())
                    .name(candidate.getName())
                    .type(candidate.getType())
                    .dateDebut(candidate.getDateDebut())
                    .location(candidate.getLocation())
                    .description(candidate.getDescription())
                    .recommendationScore(finalScore)
                    .recommendationReason(reason)
                    .build());
        }

        scoredCandidates.sort((a, b) -> Double.compare(b.getRecommendationScore(), a.getRecommendationScore()));
        return scoredCandidates.stream().limit(limit).collect(Collectors.toList());
    }

    private List<RecommendedEventDto> fallbackTopRatedEvents(List<Event> candidateEvents, int limit) {
        List<RecommendedEventDto> scored = new ArrayList<>();
        for (Event candidate : candidateEvents) {
            EventStatsDto stats = safeGetStats(candidate.getIdEvent());
            double score = (stats != null && stats.getAverageRating() > 0) ? (stats.getAverageRating() / 5.0) : 0.0;

            scored.add(RecommendedEventDto.builder()
                    .idEvent(candidate.getIdEvent())
                    .name(candidate.getName())
                    .type(candidate.getType())
                    .dateDebut(candidate.getDateDebut())
                    .location(candidate.getLocation())
                    .description(candidate.getDescription())
                    .recommendationScore(score)
                    .recommendationReason("Top rated event on YBrainy!")
                    .build());
        }
        scored.sort((a, b) -> Double.compare(b.getRecommendationScore(), a.getRecommendationScore()));
        return scored.stream().limit(limit).collect(Collectors.toList());
    }

    private List<Double> computeProfileEmbedding(List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return null;
        }

        List<List<Double>> allEmbeddings = new ArrayList<>();
        int limit = Math.min(5, descriptions.size());
        for (int i = 0; i < limit; i++) {
            List<Double> embedding = aiEmbeddingClient.getEmbeddingSync(descriptions.get(i));
            if (embedding != null && !embedding.isEmpty()) {
                allEmbeddings.add(embedding);
            }
        }

        if (allEmbeddings.isEmpty()) {
            return null;
        }

        int dimension = allEmbeddings.get(0).size();
        List<Double> average = new ArrayList<>(Collections.nCopies(dimension, 0.0));

        for (List<Double> embedding : allEmbeddings) {
            for (int i = 0; i < dimension; i++) {
                average.set(i, average.get(i) + embedding.get(i));
            }
        }

        for (int i = 0; i < dimension; i++) {
            average.set(i, average.get(i) / allEmbeddings.size());
        }

        return average;
    }

    private String generateReason(double collabScore, double contentScore, double aiScore, EventType type) {
        if (aiScore > 0.8) {
            return "Highly relevant to your recent interests.";
        } else if (collabScore > 0) {
            return "Because you matched with " + type + " events.";
        } else if (contentScore > 0.8) {
            return "Highly rated by other students.";
        }
        return "Recommended based on your profile.";
    }

    private boolean isPrimaryCandidate(Event event, List<Long> enrolledEventIds, LocalDateTime now) {
        if (!isEligibleCatalogEvent(event, enrolledEventIds)) {
            return false;
        }

        LocalDateTime end = event.getDateFin();
        LocalDateTime start = event.getDateDebut();

        if (end != null) {
            return end.isAfter(now);
        }

        return start != null && start.isAfter(now);
    }

    private boolean isRelaxedCandidate(Event event, List<Long> enrolledEventIds) {
        return isEligibleCatalogEvent(event, enrolledEventIds);
    }

    private boolean isEligibleCatalogEvent(Event event, List<Long> enrolledEventIds) {
        return event != null
                && event.getStatut() != EventStatut.ANNULE
                && event.getStatut() != EventStatut.TERMINE
                && !enrolledEventIds.contains(event.getIdEvent());
    }

    private LocalDateTime resolveEventTimelineAnchor(Event event) {
        if (event.getDateDebut() != null) {
            return event.getDateDebut();
        }
        if (event.getDateFin() != null) {
            return event.getDateFin();
        }
        return LocalDateTime.MAX;
    }

    private List<Long> safeGetEnrolledEventIds(long studentId) {
        try {
            List<Long> ids = inscriptionClient.getRegisteredEventIdsByStudent(studentId);
            return ids != null ? ids : new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Inscription service unreachable: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<FeedbackDto> safeGetStudentFeedbacks(long studentId) {
        try {
            List<FeedbackDto> feedbacks = feedbackClient.getFeedbacksByStudent(studentId);
            return feedbacks != null ? feedbacks : new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Feedback service unreachable: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private EventStatsDto safeGetStats(long eventId) {
        try {
            return feedbackClient.getStatsByEvent(eventId);
        } catch (Exception e) {
            logger.debug("Feedback stats not found or service unreachable for event {}", eventId);
            return null;
        }
    }
}
