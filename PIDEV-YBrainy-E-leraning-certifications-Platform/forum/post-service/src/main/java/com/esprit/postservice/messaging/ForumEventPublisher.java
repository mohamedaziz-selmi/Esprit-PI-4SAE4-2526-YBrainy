package com.esprit.postservice.messaging;

import com.esprit.postservice.config.RabbitMQConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishPostCreated(Long postId, Long authorId, Long threadId) {
        try {
            PostCreatedEvent event = new PostCreatedEvent(postId, authorId, threadId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_POST_CREATED, event);
            log.info("Published post.created for post={}", postId);
        } catch (Exception e) {
            log.warn("Failed to publish post.created event (RabbitMQ unavailable?): {}", e.getMessage());
        }
    }

    public void publishBestAnswer(Long postId, Long postAuthorId, Long threadId) {
        try {
            BestAnswerEvent event = new BestAnswerEvent(postId, postAuthorId, threadId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_BEST_ANSWER, event);
            log.info("Published best.answer for post={}", postId);
        } catch (Exception e) {
            log.warn("Failed to publish best.answer event (RabbitMQ unavailable?): {}", e.getMessage());
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    static class PostCreatedEvent {
        private Long postId;
        private Long authorId;
        private Long threadId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    static class BestAnswerEvent {
        private Long postId;
        private Long postAuthorId;
        private Long threadId;
    }
}
