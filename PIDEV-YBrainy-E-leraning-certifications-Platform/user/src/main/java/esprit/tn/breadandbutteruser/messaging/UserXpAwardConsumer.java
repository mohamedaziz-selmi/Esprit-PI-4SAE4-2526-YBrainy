package esprit.tn.breadandbutteruser.messaging;

import esprit.tn.breadandbutteruser.entities.XpEvent;
import esprit.tn.breadandbutteruser.services.XpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserXpAwardConsumer {

    private final XpService xpService;

    @RabbitListener(queues = "${app.rabbitmq.user-xp-award-queue}")
    public void handleXpAward(UserXpAwardMessage message) {
        if (message == null || message.getUserId() == null || message.getAmount() == null) {
            log.warn("Ignoring invalid XP award message: {}", message);
            return;
        }
        if (message.getAmount() <= 0) {
            log.warn("Ignoring non-positive XP award amount {} for user {}", message.getAmount(), message.getUserId());
            return;
        }

        XpEvent.XpSourceType sourceType = resolveSourceType(message.getSourceType());
        xpService.awardXp(message.getUserId(), message.getAmount(), sourceType, message.getDescription());
    }

    private XpEvent.XpSourceType resolveSourceType(String value) {
        if (!StringUtils.hasText(value)) {
            return XpEvent.XpSourceType.OTHER;
        }
        try {
            return XpEvent.XpSourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown XP source type '{}', falling back to OTHER", value);
            return XpEvent.XpSourceType.OTHER;
        }
    }
}
