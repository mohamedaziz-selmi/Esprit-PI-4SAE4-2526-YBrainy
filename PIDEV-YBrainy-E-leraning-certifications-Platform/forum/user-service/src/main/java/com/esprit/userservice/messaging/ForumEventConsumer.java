package com.esprit.userservice.messaging;

import com.esprit.userservice.config.RabbitMQConfig;
import com.esprit.userservice.events.*;
import com.esprit.userservice.feign.ThreadInfoFeignClient;
import com.esprit.userservice.model.XpSource;
import com.esprit.userservice.repository.UserRepository;
import com.esprit.userservice.service.NotificationService;
import com.esprit.userservice.service.XpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumEventConsumer {

    private final XpService xpService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ThreadInfoFeignClient threadInfoClient;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_THREAD_CREATED)
    public void onThreadCreated(ThreadCreatedEvent event) {
        log.info("Thread created event: user={}", event.getAuthorId());
        try { xpService.awardXp(event.getAuthorId(), XpSource.THREAD_CREATED); } catch (Exception e) {
            log.warn("Failed to award XP for thread.created: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_POST_CREATED)
    public void onPostCreated(PostCreatedEvent event) {
        log.info("Post created event: user={}, thread={}", event.getAuthorId(), event.getThreadId());
        try { xpService.awardXp(event.getAuthorId(), XpSource.POST_CREATED); } catch (Exception e) {
            log.warn("Failed to award XP for post.created: {}", e.getMessage());
        }
        // Notify thread owner (if different from post author)
        try {
            ThreadInfoFeignClient.ThreadInfo thread = threadInfoClient.getThread(event.getThreadId());
            if (thread != null && thread.getAuthorId() != null
                    && !thread.getAuthorId().equals(event.getAuthorId())) {
                String posterName = userRepository.findById(event.getAuthorId())
                        .map(u -> u.getUsername()).orElse("Someone");
                notificationService.create(
                        thread.getAuthorId(),
                        "POST_CREATED",
                        posterName + " a répondu à votre thread \"" + thread.getTitle() + "\"",
                        event.getThreadId()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create post notification: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_COMMENT_CREATED)
    public void onCommentCreated(CommentCreatedEvent event) {
        log.info("Comment created event: user={}", event.getAuthorId());
        try { xpService.awardXp(event.getAuthorId(), XpSource.COMMENT_CREATED); } catch (Exception e) {
            log.warn("Failed to award XP for comment.created: {}", e.getMessage());
        }
        // Notify thread owner (if different from commenter)
        try {
            if (event.getThreadId() != null) {
                ThreadInfoFeignClient.ThreadInfo thread = threadInfoClient.getThread(event.getThreadId());
                if (thread != null && thread.getAuthorId() != null
                        && !thread.getAuthorId().equals(event.getAuthorId())) {
                    String commenterName = userRepository.findById(event.getAuthorId())
                            .map(u -> u.getUsername()).orElse("Someone");
                    notificationService.create(
                            thread.getAuthorId(),
                            "COMMENT_CREATED",
                            commenterName + " a commenté dans votre thread \"" + thread.getTitle() + "\"",
                            event.getThreadId()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to create comment notification: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_UPVOTE_RECEIVED)
    public void onUpvoteReceived(UpvoteReceivedEvent event) {
        log.info("Upvote received: threadAuthor={}", event.getThreadAuthorId());
        try { xpService.awardXp(event.getThreadAuthorId(), XpSource.UPVOTE_RECEIVED); } catch (Exception e) {
            log.warn("Failed to award XP for upvote.received: {}", e.getMessage());
        }
        try {
            String voterName = userRepository.findById(event.getVoterId())
                    .map(u -> u.getUsername()).orElse("Someone");
            String threadTitle = "";
            try {
                ThreadInfoFeignClient.ThreadInfo thread = threadInfoClient.getThread(event.getThreadId());
                if (thread != null) threadTitle = " \"" + thread.getTitle() + "\"";
            } catch (Exception ignored) {}
            notificationService.create(
                    event.getThreadAuthorId(),
                    "VOTED",
                    voterName + " a upvoté votre thread" + threadTitle,
                    event.getThreadId()
            );
        } catch (Exception e) {
            log.warn("Failed to create upvote notification: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BEST_ANSWER)
    public void onBestAnswer(BestAnswerEvent event) {
        log.info("Best answer: postAuthor={}", event.getPostAuthorId());
        try { xpService.awardXp(event.getPostAuthorId(), XpSource.BEST_ANSWER); } catch (Exception e) {
            log.warn("Failed to award XP for best.answer: {}", e.getMessage());
        }
        try {
            notificationService.create(
                    event.getPostAuthorId(),
                    "BEST_ANSWER",
                    "Votre réponse a été marquée comme la meilleure réponse !",
                    event.getThreadId()
            );
        } catch (Exception e) {
            log.warn("Failed to create best_answer notification: {}", e.getMessage());
        }
    }
}
