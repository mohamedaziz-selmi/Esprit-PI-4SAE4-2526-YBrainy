package tn.esprit.eventservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tn.esprit.eventservice.config.RabbitMqConfig;
import tn.esprit.eventservice.dto.EventCreatedMessage;
import tn.esprit.eventservice.entity.Event;

@Service
@RequiredArgsConstructor
public class EventCreatedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Event event) {
        EventCreatedMessage payload = new EventCreatedMessage(
                event.getIdEvent(),
                event.getName(),
                event.getReferenceEvent(),
                event.getLocation(),
                event.getCapacite(),
                event.getDateDebut(),
                event.getDateFin(),
                event.getAdminId()
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENT_EXCHANGE,
                RabbitMqConfig.EVENT_CREATED_ROUTING_KEY,
                payload
        );
    }
}
