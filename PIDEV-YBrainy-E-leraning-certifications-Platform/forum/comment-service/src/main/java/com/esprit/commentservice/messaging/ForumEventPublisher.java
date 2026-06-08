package com.esprit.commentservice.messaging;

import com.esprit.commentservice.config.RabbitMQConfig;
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

    public void publishCommentCreated(Long commentId, Long authorId, Long postId, Long threadId) {
        try {
            CommentCreatedEvent event = new CommentCreatedEvent(commentId, authorId, postId, threadId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_COMMENT_CREATED, event);
            log.info("Published comment.created for comment={}", commentId);
        } catch (Exception e) {
            log.warn("Failed to publish comment.created event (RabbitMQ unavailable?): {}", e.getMessage());
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    static class CommentCreatedEvent {
        private Long commentId;
        private Long authorId;
        private Long postId;
        private Long threadId;
    }
}
