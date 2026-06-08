package tn.esprit.warningbanappealservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;
    @Value("${app.rabbitmq.warning-created-routing-key}")
    private String warningCreatedRoutingKey;
    @Value("${app.rabbitmq.warning-updated-routing-key}")
    private String warningUpdatedRoutingKey;
    @Value("${app.rabbitmq.warning-deleted-routing-key}")
    private String warningDeletedRoutingKey;
    @Value("${app.rabbitmq.appeal-created-routing-key}")
    private String appealCreatedRoutingKey;
    @Value("${app.rabbitmq.appeal-updated-routing-key}")
    private String appealUpdatedRoutingKey;
    @Value("${app.rabbitmq.appeal-deleted-routing-key}")
    private String appealDeletedRoutingKey;

    public void publishWarningCreated(String warningId, Long userId) {
        publish(warningCreatedRoutingKey, warningEvent("WARNING_CREATED", warningId, userId));
    }

    public void publishWarningUpdated(String warningId, Long userId) {
        publish(warningUpdatedRoutingKey, warningEvent("WARNING_UPDATED", warningId, userId));
    }

    public void publishWarningDeleted(String warningId, Long userId) {
        publish(warningDeletedRoutingKey, warningEvent("WARNING_DELETED", warningId, userId));
    }

    public void publishAppealCreated(String appealId, Long userId) {
        publish(appealCreatedRoutingKey, appealEvent("BAN_APPEAL_CREATED", appealId, userId));
    }

    public void publishAppealUpdated(String appealId, Long userId) {
        publish(appealUpdatedRoutingKey, appealEvent("BAN_APPEAL_UPDATED", appealId, userId));
    }

    public void publishAppealDeleted(String appealId, Long userId) {
        publish(appealDeletedRoutingKey, appealEvent("BAN_APPEAL_DELETED", appealId, userId));
    }

    private DomainEventMessage warningEvent(String type, String warningId, Long userId) {
        return DomainEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .warningId(warningId)
                .userId(userId)
                .occurredAt(Instant.now())
                .build();
    }

    private DomainEventMessage appealEvent(String type, String appealId, Long userId) {
        return DomainEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .appealId(appealId)
                .userId(userId)
                .occurredAt(Instant.now())
                .build();
    }

    private void publish(String routingKey, DomainEventMessage message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
        } catch (RuntimeException ex) {
            log.warn("RabbitMQ publish skipped for event {} on routing key {}: {}",
                    message.getEventType(), routingKey, ex.getMessage());
        }
    }
}
