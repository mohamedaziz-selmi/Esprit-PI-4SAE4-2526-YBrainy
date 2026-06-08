package tn.esprit.eventservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.eventservice.client.AiEmbeddingClient;
import tn.esprit.eventservice.client.FeedbackClient;
import tn.esprit.eventservice.client.InscriptionClient;
import tn.esprit.eventservice.dto.EventStatsDto;
import tn.esprit.eventservice.dto.FeedbackDto;
import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.entity.EventStatut;
import tn.esprit.eventservice.entity.EventType;
import tn.esprit.eventservice.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock EventRepository eventRepository;
    @Mock InscriptionClient inscriptionClient;
    @Mock FeedbackClient feedbackClient;
    @Mock AiEmbeddingClient aiEmbeddingClient;
    @InjectMocks RecommendationService service;

    @Test
    @DisplayName("getRecommendationsForStudent() keeps ongoing events as primary candidates when dateFin is in the future")
    void getRecommendations_keepsOngoingEvents() {
        Event ongoing = event(10L, "Ongoing AI Lab", EventType.ATELIER, EventStatut.PUBLIE,
                LocalDateTime.now().minusHours(3), LocalDateTime.now().plusDays(1), "Hands-on AI lab");
        Event enrolledHistory = event(20L, "Past Webinar", EventType.ATELIER, EventStatut.PUBLIE,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(6), "History desc");

        when(eventRepository.findAll()).thenReturn(List.of(ongoing, enrolledHistory));
        when(inscriptionClient.getRegisteredEventIdsByStudent(5L)).thenReturn(List.of(20L));
        when(feedbackClient.getFeedbacksByStudent(5L)).thenReturn(Collections.emptyList());
        when(eventRepository.findById(20L)).thenReturn(Optional.of(enrolledHistory));
        when(feedbackClient.getStatsByEvent(10L)).thenReturn(new EventStatsDto(10L, 4.5, 3, Map.of(5, 3L)));
        when(aiEmbeddingClient.getEmbeddingSync("History desc")).thenReturn(List.of(1.0, 0.0));
        when(aiEmbeddingClient.getEmbeddingSync("Hands-on AI lab")).thenReturn(List.of(1.0, 0.0));
        when(aiEmbeddingClient.computeCosineSimilarity(List.of(1.0, 0.0), List.of(1.0, 0.0))).thenReturn(1.0);

        var result = service.getRecommendationsForStudent(5L, 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIdEvent()).isEqualTo(10L);
        assertThat(result.get(0).getRecommendationReason()).isEqualTo("Highly relevant to your recent interests.");
    }

    @Test
    @DisplayName("getRecommendationsForStudent() falls back to relaxed candidates when all events already started")
    void getRecommendations_relaxedFallbackWhenPrimaryEmpty() {
        Event alreadyStarted = event(30L, "Deep Learning Sprint", EventType.HACKATHON, EventStatut.PUBLIE,
                LocalDateTime.now().minusDays(1), null, "Sprint description");

        when(eventRepository.findAll()).thenReturn(List.of(alreadyStarted));
        when(inscriptionClient.getRegisteredEventIdsByStudent(9L)).thenReturn(Collections.emptyList());
        when(feedbackClient.getFeedbacksByStudent(9L)).thenReturn(Collections.emptyList());
        when(feedbackClient.getStatsByEvent(30L)).thenReturn(new EventStatsDto(30L, 4.8, 12, Map.of(5, 12L)));

        var result = service.getRecommendationsForStudent(9L, 2);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIdEvent()).isEqualTo(30L);
        assertThat(result.get(0).getRecommendationReason()).isEqualTo("Top rated event on YBrainy!");
    }

    @Test
    @DisplayName("getRecommendationsForStudent() excludes enrolled, cancelled, and finished events")
    void getRecommendations_filtersIneligibleEvents() {
        Event enrolled = event(40L, "Already Joined", EventType.WEBINAIRE, EventStatut.PUBLIE,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), "Joined");
        Event cancelled = event(41L, "Cancelled", EventType.WEBINAIRE, EventStatut.ANNULE,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), "Cancelled");
        Event finished = event(42L, "Finished", EventType.WEBINAIRE, EventStatut.TERMINE,
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2), "Finished");
        Event eligible = event(43L, "Eligible", EventType.WEBINAIRE, EventStatut.PUBLIE,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3), "Eligible");

        when(eventRepository.findAll()).thenReturn(List.of(enrolled, cancelled, finished, eligible));
        when(inscriptionClient.getRegisteredEventIdsByStudent(12L)).thenReturn(List.of(40L));
        when(feedbackClient.getFeedbacksByStudent(12L)).thenReturn(List.of(
                FeedbackDto.builder().eventId(40L).rating(5).build()
        ));
        when(eventRepository.findById(40L)).thenReturn(Optional.of(enrolled));
        when(feedbackClient.getStatsByEvent(43L)).thenReturn(new EventStatsDto(43L, 4.0, 5, Map.of(4, 5L)));
        when(aiEmbeddingClient.getEmbeddingSync("Joined")).thenReturn(List.of(1.0));
        when(aiEmbeddingClient.getEmbeddingSync("Eligible")).thenReturn(List.of(1.0));
        when(aiEmbeddingClient.computeCosineSimilarity(List.of(1.0), List.of(1.0))).thenReturn(1.0);

        var result = service.getRecommendationsForStudent(12L, 5);

        assertThat(result).extracting("idEvent").containsExactly(43L);
    }

    private Event event(
            long id,
            String name,
            EventType type,
            EventStatut statut,
            LocalDateTime start,
            LocalDateTime end,
            String description
    ) {
        Event event = new Event();
        event.setIdEvent(id);
        event.setName(name);
        event.setType(type);
        event.setStatut(statut);
        event.setDateDebut(start);
        event.setDateFin(end);
        event.setDescription(description);
        event.setLocation("Tunis");
        return event;
    }
}
