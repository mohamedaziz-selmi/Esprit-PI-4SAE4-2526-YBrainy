package tn.esprit.inscriptionservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import tn.esprit.inscriptionservice.config.RabbitMqConfig;
import tn.esprit.inscriptionservice.dto.EventCreatedMessage;
import tn.esprit.inscriptionservice.entity.AdminNotification;
import tn.esprit.inscriptionservice.repository.AdminNotificationRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventCreatedListener {

    private final AdminNotificationRepository adminNotificationRepository;

    @RabbitListener(queues = RabbitMqConfig.EVENT_CREATED_QUEUE)
    public void handleEventCreated(EventCreatedMessage message) {
        AdminNotification notification = new AdminNotification();
        notification.setCreatedAt(LocalDateTime.now());
        notification.setType("EVENT_CREATED");
        notification.setTitle("New event published");
        notification.setMessage(buildMessage(message));
        notification.setEventId(message.eventId());
        notification.setStudentId(0L);
        adminNotificationRepository.save(notification);
    }

    private String buildMessage(EventCreatedMessage message) {
        String eventName = message.name() != null && !message.name().isBlank()
                ? message.name()
                : "Event #" + message.eventId();
        String reference = message.referenceEvent() != null && !message.referenceEvent().isBlank()
                ? " (" + message.referenceEvent() + ")"
                : "";
        return eventName + reference + " is now available for registration.";
    }
}
