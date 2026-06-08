package tn.esprit.eventservice.service;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.eventservice.client.FeedbackClient;
import tn.esprit.eventservice.client.InscriptionClient;
import tn.esprit.eventservice.client.RecommendationMlClient;
import tn.esprit.eventservice.config.RecommendationMlProperties;
import tn.esprit.eventservice.dto.EventStatsDto;
import tn.esprit.eventservice.dto.FeedbackDto;
import tn.esprit.eventservice.dto.RecommendedEventDto;
import tn.esprit.eventservice.dto.ml.MlHistoryItemDto;
import tn.esprit.eventservice.dto.ml.MlRecommendationCandidateDto;
import tn.esprit.eventservice.dto.ml.MlRecommendationRequestDto;
import tn.esprit.eventservice.dto.ml.MlRecommendationResponseDto;
import tn.esprit.eventservice.dto.ml.MlRecommendedEventDto;
import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.entity.EventStatut;
import tn.esprit.eventservice.repository.EventRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PythonRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(PythonRecommendationService.class);
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EventRepository eventRepository;
    private final InscriptionClient inscriptionClient;
    private final FeedbackClient feedbackClient;
    private final RecommendationMlClient recommendationMlClient;
    private final RecommendationMlProperties recommendationMlProperties;

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

        MlRecommendationRequestDto request = new MlRecommendationRequestDto();
        request.setStudentId(studentId);
        request.setLimit(limit);
        request.setSvdWeight(recommendationMlProperties.getPreferenceWeight());
        request.setContentWeight(recommendationMlProperties.getContentWeight());
        request.setCandidateEvents(buildCandidateDtos(candidateEvents));
        request.setHistory(buildHistoryDtos(enrolledEventIds, studentFeedbacks));

        logger.info(
                "Calling Python recommendation service for studentId={} with {} candidates and {} history items",
                studentId,
                request.getCandidateEvents() != null ? request.getCandidateEvents().size() : 0,
                request.getHistory() != null ? request.getHistory().size() : 0
        );

        MlRecommendationResponseDto response = recommendationMlClient.recommendTopN(request);
        if (response == null || response.getRecommendations() == null) {
            throw new IllegalStateException("Recommendation ML service returned no recommendations");
        }

        return response.getRecommendations().stream()
                .map(this::toRecommendedEventDto)
                .collect(Collectors.toList());
    }

    private List<MlRecommendationCandidateDto> buildCandidateDtos(List<Event> candidateEvents) {
        return candidateEvents.stream()
                .map(event -> {
                    MlRecommendationCandidateDto dto = new MlRecommendationCandidateDto();
                    dto.setEventId(event.getIdEvent());
                    dto.setName(event.getName());
                    dto.setType(event.getType() != null ? event.getType().name() : null);
                    dto.setDescription(event.getDescription());
                    dto.setLocation(event.getLocation());
                    dto.setDateDebut(formatDateTime(event.getDateDebut()));
                    dto.setAverageRating(resolveAverageRating(event.getIdEvent()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<MlHistoryItemDto> buildHistoryDtos(List<Long> enrolledEventIds, List<FeedbackDto> studentFeedbacks) {
        Map<Long, MlHistoryItemDto> historyByEventId = new LinkedHashMap<>();

        for (Long eventId : enrolledEventIds) {
            eventRepository.findById(eventId).ifPresent(event -> {
                MlHistoryItemDto dto = new MlHistoryItemDto();
                dto.setEventId(eventId);
                dto.setRating(4);
                dto.setEventType(event.getType() != null ? event.getType().name() : null);
                dto.setDescription(defaultString(event.getDescription()));
                historyByEventId.put(eventId, dto);
            });
        }

        for (FeedbackDto feedback : studentFeedbacks) {
            eventRepository.findById(feedback.getEventId()).ifPresent(event -> {
                MlHistoryItemDto dto = new MlHistoryItemDto();
                dto.setEventId(feedback.getEventId());
                dto.setRating(feedback.getRating());
                dto.setEventType(event.getType() != null ? event.getType().name() : null);
                dto.setDescription(firstNonBlank(feedback.getComment(), event.getDescription()));
                historyByEventId.put(feedback.getEventId(), dto);
            });
        }

        return new ArrayList<>(historyByEventId.values());
    }

    private RecommendedEventDto toRecommendedEventDto(MlRecommendedEventDto item) {
        Event event = eventRepository.findById(item.getEventId()).orElse(null);

        return RecommendedEventDto.builder()
                .idEvent(item.getEventId())
                .name(item.getName())
                .type(event != null ? event.getType() : null)
                .dateDebut(item.getDateDebut())
                .location(item.getLocation())
                .description(item.getDescription())
                .recommendationScore(item.getFinalScore())
                .recommendationReason(item.getReason())
                .build();
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

    private Double resolveAverageRating(long eventId) {
        EventStatsDto stats = safeGetStats(eventId);
        return (stats != null && stats.getAverageRating() > 0) ? stats.getAverageRating() : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return defaultString(fallback);
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(ISO_DATE_TIME) : null;
    }
}
