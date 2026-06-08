package com.esprit.threadservice.messaging;

import com.esprit.threadservice.config.RabbitMQConfig;
import com.esprit.threadservice.events.ThreadCreatedEvent;
import com.esprit.threadservice.events.UpvoteReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishThreadCreated(Long threadId, Long authorId, String title) {
        try {
            ThreadCreatedEvent event = new ThreadCreatedEvent(threadId, authorId, title);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_THREAD_CREATED, event);
            log.info("Published thread.created for thread={}", threadId);
        } catch (Exception e) {
            log.warn("Failed to publish thread.created event (RabbitMQ unavailable?): {}", e.getMessage());
        }
    }

    public void publishUpvoteReceived(Long threadId, Long threadAuthorId, Long voterId) {
        try {
            UpvoteReceivedEvent event = new UpvoteReceivedEvent(threadId, threadAuthorId, voterId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_UPVOTE_RECEIVED, event);
            log.info("Published upvote.received for thread={}", threadId);
        } catch (Exception e) {
            log.warn("Failed to publish upvote.received event (RabbitMQ unavailable?): {}", e.getMessage());
        }
    }
}
