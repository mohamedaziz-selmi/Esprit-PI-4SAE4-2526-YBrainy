package tn.esprit.warningbanappealservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tn.esprit.warningbanappealservice.service.BanAppealService;
import tn.esprit.warningbanappealservice.service.WarningService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

    private final WarningService warningService;
    private final BanAppealService banAppealService;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @RabbitListener(queues = "${app.rabbitmq.user-deleted-queue}")
    public void handleUserDeleted(UserEventMessage message) {
        if (message == null || message.getUserId() == null) {
            log.warn("Ignoring invalid user deletion message: {}", message);
            return;
        }
        String idempotencyKey = StringUtils.hasText(message.getEventId())
                ? message.getEventId()
                : "USER_DELETED:" + message.getUserId();
        if (!processedEventIds.add(idempotencyKey)) {
            log.info("Skipping duplicate user deletion event {}", idempotencyKey);
            return;
        }
        if (StringUtils.hasText(message.getEventType()) && !"USER_DELETED".equals(message.getEventType())) {
            log.warn("Ignoring unexpected user event type {} for user {}", message.getEventType(), message.getUserId());
            return;
        }

        warningService.deleteByUserId(message.getUserId());
        banAppealService.deleteByUserId(message.getUserId());
    }
}
