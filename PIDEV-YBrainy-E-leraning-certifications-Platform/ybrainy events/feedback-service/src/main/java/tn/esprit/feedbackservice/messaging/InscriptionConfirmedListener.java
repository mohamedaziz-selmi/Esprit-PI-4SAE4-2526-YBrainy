package tn.esprit.feedbackservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import tn.esprit.feedbackservice.config.RabbitMqConfig;
import tn.esprit.feedbackservice.dto.InscriptionConfirmedMessage;
import tn.esprit.feedbackservice.entity.FeedbackReminder;
import tn.esprit.feedbackservice.repository.FeedbackReminderRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InscriptionConfirmedListener {

    private final FeedbackReminderRepository feedbackReminderRepository;

    @RabbitListener(queues = RabbitMqConfig.INSCRIPTION_CONFIRMED_QUEUE)
    public void handleInscriptionConfirmed(InscriptionConfirmedMessage message) {
        if (feedbackReminderRepository.existsByInscriptionId(message.inscriptionId())) {
            return;
        }

        FeedbackReminder reminder = new FeedbackReminder();
        reminder.setInscriptionId(message.inscriptionId());
        reminder.setEventId(message.eventId());
        reminder.setStudentId(message.studentId());
        reminder.setCreatedAt(message.confirmedAt() != null ? message.confirmedAt() : LocalDateTime.now());
        reminder.setSource(message.source());
        reminder.setStatut(message.statut());
        feedbackReminderRepository.save(reminder);
    }
}
