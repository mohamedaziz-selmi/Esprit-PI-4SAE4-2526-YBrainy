package esprit.tn.breadandbutteruser.messaging;

import esprit.tn.breadandbutteruser.dto.XpEventDto;
import esprit.tn.breadandbutteruser.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.user-created-routing-key}")
    private String userCreatedRoutingKey;

    @Value("${app.rabbitmq.user-updated-routing-key}")
    private String userUpdatedRoutingKey;

    @Value("${app.rabbitmq.user-deleted-routing-key}")
    private String userDeletedRoutingKey;

    @Value("${app.rabbitmq.user-login-routing-key}")
    private String userLoginRoutingKey;

    @Value("${app.rabbitmq.user-xp-awarded-routing-key}")
    private String userXpAwardedRoutingKey;

    @Value("${app.rabbitmq.user-level-up-routing-key}")
    private String userLevelUpRoutingKey;

    public void publishCreated(User user) {
        publish(userCreatedRoutingKey, buildEvent("USER_CREATED", user, Map.of()));
    }

    public void publishUpdated(User user) {
        publish(userUpdatedRoutingKey, buildEvent("USER_UPDATED", user, Map.of()));
    }

    public void publishDeleted(User user) {
        publish(userDeletedRoutingKey, buildEvent("USER_DELETED", user, Map.of()));
    }

    public void publishLogin(User user) {
        publish(userLoginRoutingKey, buildEvent("USER_LOGIN", user, Map.of()));
    }

    public void publishXpAwarded(User user, XpEventDto xpEvent) {
        publish(userXpAwardedRoutingKey, buildEvent("USER_XP_AWARDED", user, xpMetadata(xpEvent)));
    }

    public void publishLevelUp(User user, XpEventDto xpEvent) {
        publish(userLevelUpRoutingKey, buildEvent("USER_LEVEL_UP", user, xpMetadata(xpEvent)));
    }

    private void publish(String routingKey, UserEventMessage message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
        } catch (RuntimeException ex) {
            log.warn("RabbitMQ publish skipped for event {} on routing key {}: {}",
                    message.getEventType(), routingKey, ex.getMessage());
        }
    }

    private UserEventMessage buildEvent(String eventType, User user, Map<String, Object> metadata) {
        return UserEventMessage.builder()
                .eventType(eventType)
                .userId(user.getUserId())
                .keycloakUserId(user.getKeycloakUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .xp(user.getXp())
                .level(user.getLevel())
                .occurredAt(Instant.now())
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> xpMetadata(XpEventDto xpEvent) {
        Map<String, Object> metadata = new HashMap<>();
        if (xpEvent == null) {
            return metadata;
        }
        metadata.put("xpEventId", xpEvent.getId());
        metadata.put("sourceType", xpEvent.getSourceType());
        metadata.put("amount", xpEvent.getAmount());
        metadata.put("newTotal", xpEvent.getNewTotal());
        metadata.put("newLevel", xpEvent.getNewLevel());
        metadata.put("levelUp", xpEvent.isLevelUp());
        metadata.put("description", xpEvent.getDescription());
        metadata.put("createdAt", xpEvent.getCreatedAt());
        return metadata;
    }
}
