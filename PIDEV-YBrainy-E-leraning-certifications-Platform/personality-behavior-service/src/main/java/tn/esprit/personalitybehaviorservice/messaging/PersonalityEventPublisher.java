package tn.esprit.personalitybehaviorservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.esprit.personalitybehaviorservice.dto.BehaviorResponseDto;
import tn.esprit.personalitybehaviorservice.dto.PersonalityResponseDto;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonalityEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.personality-created-routing-key}")
    private String personalityCreatedRoutingKey;

    @Value("${app.rabbitmq.personality-updated-routing-key}")
    private String personalityUpdatedRoutingKey;

    @Value("${app.rabbitmq.behavior-created-routing-key}")
    private String behaviorCreatedRoutingKey;

    @Value("${app.rabbitmq.behavior-updated-routing-key}")
    private String behaviorUpdatedRoutingKey;

    public void publishPersonalityCreated(PersonalityResponseDto personality) {
        publish(personalityCreatedRoutingKey, build("PERSONALITY_CREATED", personality));
    }

    public void publishPersonalityUpdated(PersonalityResponseDto personality) {
        publish(personalityUpdatedRoutingKey, build("PERSONALITY_UPDATED", personality));
    }

    public void publishBehaviorCreated(BehaviorResponseDto behavior) {
        publish(behaviorCreatedRoutingKey, build("BEHAVIOR_CREATED", behavior));
    }

    public void publishBehaviorUpdated(BehaviorResponseDto behavior) {
        publish(behaviorUpdatedRoutingKey, build("BEHAVIOR_UPDATED", behavior));
    }

    private void publish(String routingKey, PersonalityEventMessage message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
        } catch (RuntimeException ex) {
            log.warn("RabbitMQ publish skipped for event {} on routing key {}: {}",
                    message.getEventType(), routingKey, ex.getMessage());
        }
    }

    private PersonalityEventMessage build(String eventType, PersonalityResponseDto personality) {
        return PersonalityEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .personalityId(personality.getPersonalityId())
                .behaviorId(personality.getBehavior() != null ? personality.getBehavior().getBehaviorId() : null)
                .userId(personality.getUserId())
                .occurredAt(Instant.now())
                .build();
    }

    private PersonalityEventMessage build(String eventType, BehaviorResponseDto behavior) {
        return PersonalityEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .behaviorId(behavior.getBehaviorId())
                .userId(behavior.getUserId())
                .occurredAt(Instant.now())
                .build();
    }
}
