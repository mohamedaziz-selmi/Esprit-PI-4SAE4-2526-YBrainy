package tn.esprit.feedbackservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.feedbackservice.client.EventClient;
import tn.esprit.feedbackservice.client.InscriptionClient;
import tn.esprit.feedbackservice.dto.EventDto;
import tn.esprit.feedbackservice.dto.FeedbackRequestDto;
import tn.esprit.feedbackservice.entity.Feedback;
import tn.esprit.feedbackservice.entity.FeedbackStatut;
import tn.esprit.feedbackservice.repository.FeedbackRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImpTest {

    @Mock FeedbackRepository feedbackRepository;
    @Mock InscriptionClient inscriptionClient;
    @Mock EventClient eventClient;
    @InjectMocks FeedbackServiceImp service;

    @Test
    @DisplayName("submitFeedback() saves published feedback when event is finished and inscription is confirmed")
    void submitFeedback_success() {
        FeedbackRequestDto dto = new FeedbackRequestDto(7L, 70L, 5, "Very useful event", "positive");
        Feedback saved = feedback(100L, 7L, 70L, 5, "Very useful event", "positive", FeedbackStatut.PUBLIE);

        when(eventClient.findById(70L)).thenReturn(Optional.of(finishedEvent(70L)));
        when(inscriptionClient.hasConfirmedInscription(7L, 70L)).thenReturn(true);
        when(feedbackRepository.existsByStudentIdAndEventId(7L, 70L)).thenReturn(false);
        when(feedbackRepository.save(any())).thenReturn(saved);

        Feedback result = service.submitFeedback(dto);

        assertThat(result.getIdFeedback()).isEqualTo(100L);
        assertThat(result.getStatut()).isEqualTo(FeedbackStatut.PUBLIE);
        assertThat(result.getSentimentLabel()).isEqualTo("positive");
        verify(feedbackRepository).save(any());
    }

    @Test
    @DisplayName("submitFeedback() rejects feedback when event is not finished")
    void submitFeedback_eventNotFinished_throws() {
        FeedbackRequestDto dto = new FeedbackRequestDto(8L, 80L, 4, "Soon", null);
        EventDto notFinished = new EventDto(80L, "Hackathon", "PUBLIE");

        when(eventClient.findById(80L)).thenReturn(Optional.of(notFinished));

        assertThatThrownBy(() -> service.submitFeedback(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("after the event has ended");
    }

    @Test
    @DisplayName("submitFeedback() rejects feedback when inscription is not confirmed")
    void submitFeedback_notConfirmed_throws() {
        FeedbackRequestDto dto = new FeedbackRequestDto(9L, 90L, 4, "Denied", null);

        when(eventClient.findById(90L)).thenReturn(Optional.of(finishedEvent(90L)));
        when(inscriptionClient.hasConfirmedInscription(9L, 90L)).thenReturn(false);

        assertThatThrownBy(() -> service.submitFeedback(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONFIRMEE inscription required");
    }

    @Test
    @DisplayName("updateFeedback() rejects updates from another student")
    void updateFeedback_wrongStudent_throws() {
        Feedback existing = feedback(101L, 10L, 100L, 3, "Old", "neutral", FeedbackStatut.PUBLIE);
        FeedbackRequestDto dto = new FeedbackRequestDto(11L, 100L, 5, "New", "positive");

        when(feedbackRepository.findById(101L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateFeedback(101L, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("your own feedback");
    }

    @Test
    @DisplayName("updateFeedback() updates sentimentLabel when provided")
    void updateFeedback_updatesSentimentLabel() {
        Feedback existing = feedback(104L, 15L, 150L, 4, "Old", "neutral", FeedbackStatut.PUBLIE);
        FeedbackRequestDto dto = new FeedbackRequestDto(15L, 150L, 5, "New comment", "positive");

        when(feedbackRepository.findById(104L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Feedback updated = service.updateFeedback(104L, dto);

        assertThat(updated.getRating()).isEqualTo(5);
        assertThat(updated.getComment()).isEqualTo("New comment");
        assertThat(updated.getSentimentLabel()).isEqualTo("positive");
    }

    @Test
    @DisplayName("submitFeedback() rejects invalid sentimentLabel")
    void submitFeedback_invalidSentimentLabel_throws() {
        FeedbackRequestDto dto = new FeedbackRequestDto(16L, 160L, 4, "Confusing event", "excited");

        when(eventClient.findById(160L)).thenReturn(Optional.of(finishedEvent(160L)));
        when(inscriptionClient.hasConfirmedInscription(16L, 160L)).thenReturn(true);
        when(feedbackRepository.existsByStudentIdAndEventId(16L, 160L)).thenReturn(false);

        assertThatThrownBy(() -> service.submitFeedback(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sentimentLabel must be one of");
    }

    @Test
    @DisplayName("getFeedbacksByStudent() delegates to repository in descending creation order")
    void getFeedbacksByStudent_delegates() {
        List<Feedback> expected = List.of(
                feedback(102L, 12L, 120L, 5, "Recent", "positive", FeedbackStatut.PUBLIE),
                feedback(103L, 12L, 121L, 4, "Older", "neutral", FeedbackStatut.PUBLIE)
        );

        when(feedbackRepository.findByStudentIdOrderByDateCreationDesc(12L)).thenReturn(expected);

        assertThat(service.getFeedbacksByStudent(12L)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("getStatsByEvent() builds average, totals, and rating distribution")
    void getStatsByEvent_buildsAggregates() {
        when(feedbackRepository.findAverageRatingByEventId(130L)).thenReturn(4.26);
        when(feedbackRepository.findRatingDistributionByEventId(130L)).thenReturn(List.of(
                new Object[]{4, 2L},
                new Object[]{5, 3L}
        ));

        var result = service.getStatsByEvent(130L);

        assertThat(result.averageRating()).isEqualTo(4.3);
        assertThat(result.totalFeedbacks()).isEqualTo(5L);
        assertThat(result.ratingDistribution()).containsEntry(4, 2L).containsEntry(5, 3L).containsEntry(1, 0L);
    }

    private EventDto finishedEvent(long id) {
        return new EventDto(id, "Finished event", "TERMINE");
    }

    private Feedback feedback(long id, long studentId, long eventId, int rating, String comment, String sentimentLabel, FeedbackStatut statut) {
        Feedback feedback = new Feedback();
        feedback.setIdFeedback(id);
        feedback.setStudentId(studentId);
        feedback.setEventId(eventId);
        feedback.setRating(rating);
        feedback.setComment(comment);
        feedback.setSentimentLabel(sentimentLabel);
        feedback.setDateCreation(LocalDateTime.now());
        feedback.setStatut(statut);
        return feedback;
    }
}
