package esprit.tn.breadandbutteruser.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import esprit.tn.breadandbutteruser.client.ThreadInfoFeignClient;
import esprit.tn.breadandbutteruser.config.ForumRabbitMQConfig;
import esprit.tn.breadandbutteruser.entities.XpEvent;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import esprit.tn.breadandbutteruser.services.NotificationService;
import esprit.tn.breadandbutteruser.services.XpService;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    // ── Event DTOs (match what forum services publish) ────────────────────────

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThreadCreatedEvent {
        private Long authorId;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostCreatedEvent {
        private Long authorId;
        private Long threadId;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentCreatedEvent {
        private Long authorId;
        private Long threadId;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpvoteReceivedEvent {
        private Long threadAuthorId;
        private Long voterId;
        private Long threadId;
    }

    @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BestAnswerEvent {
        private Long postAuthorId;
        private Long threadId;
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    @RabbitListener(queues = ForumRabbitMQConfig.QUEUE_THREAD_CREATED)
    public void onThreadCreated(ThreadCreatedEvent event) {
        log.info("Forum event: thread created by user={}", event.getAuthorId());
        awardXp(event.getAuthorId(), 20, XpEvent.XpSourceType.THREAD_CREATED, "Thread created");
    }

    @RabbitListener(queues = ForumRabbitMQConfig.QUEUE_POST_CREATED)
    public void onPostCreated(PostCreatedEvent event) {
        log.info("Forum event: post created by user={} on thread={}", event.getAuthorId(), event.getThreadId());
        awardXp(event.getAuthorId(), 10, XpEvent.XpSourceType.POST_CREATED, "Post created");
        try {
            ThreadInfoFeignClient.ThreadInfo thread = threadInfoClient.getThread(event.getThreadId());
            if (thread != null && thread.getAuthorId() != null
                    && !thread.getAuthorId().equals(event.getAuthorId())) {
                String poster = username(event.getAuthorId());
                notificationService.create(
                        thread.getAuthorId(), "POST_CREATED",
                        poster + " a répondu à votre thread \"" + thread.getTitle() + "\"",
                        event.getThreadId());
            }
        } catch (Exception e) {
            log.warn("Post notification failed: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = ForumRabbitMQConfig.QUEUE_COMMENT_CREATED)
    public void onCommentCreated(CommentCreatedEvent event) {
        log.info("Forum event: comment by user={}", event.getAuthorId());
        awardXp(event.getAuthorId(), 5, XpEvent.XpSourceType.COMMENT_CREATED, "Comment created");
        try {
            if (event.getThreadId() != null) {
                ThreadInfoFeignClient.ThreadInfo thread = threadInfoClient.getThread(event.getThreadId());
                if (thread != null && thread.getAuthorId() != null
                        && !thread.getAuthorId().equals(event.getAuthorId())) {
                    String commenter = username(event.getAuthorId());
                    notificationService.create(
                            thread.getAuthorId(), "COMMENT_CREATED",
                            commenter + " a commenté dans votre thread \"" + thread.getTitle() + "\"",
                            event.getThreadId());
                }
            }
        } catch (Exception e) {
            log.warn("Comment notification failed: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = ForumRabbitMQConfig.QUEUE_UPVOTE_RECEIVED)
    public void onUpvoteReceived(UpvoteReceivedEvent event) {
        log.info("Forum event: upvote received by user={}", event.getThreadAuthorId());
        awardXp(event.getThreadAuthorId(), 3, XpEvent.XpSourceType.UPVOTE_RECEIVED, "Upvote received");
        try {
            String voter = username(event.getVoterId());
            String title = "";
            try {
                ThreadInfoFeignClient.ThreadInfo thread = threadInfoClient.getThread(event.getThreadId());
                if (thread != null) title = " \"" + thread.getTitle() + "\"";
            } catch (Exception ignored) {}
            notificationService.create(
                    event.getThreadAuthorId(), "VOTED",
                    voter + " a upvoté votre thread" + title,
                    event.getThreadId());
        } catch (Exception e) {
            log.warn("Upvote notification failed: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = ForumRabbitMQConfig.QUEUE_BEST_ANSWER)
    public void onBestAnswer(BestAnswerEvent event) {
        log.info("Forum event: best answer for user={}", event.getPostAuthorId());
        awardXp(event.getPostAuthorId(), 50, XpEvent.XpSourceType.BEST_ANSWER, "Best answer awarded");
        try {
            notificationService.create(
                    event.getPostAuthorId(), "BEST_ANSWER",
                    "Votre réponse a été marquée comme la meilleure réponse !",
                    event.getThreadId());
        } catch (Exception e) {
            log.warn("Best answer notification failed: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void awardXp(Long userId, int amount, XpEvent.XpSourceType source, String desc) {
        if (userId == null) return;
        try {
            xpService.awardXp(userId, amount, source, desc);
        } catch (Exception e) {
            log.warn("Failed to award XP (user={}, source={}): {}", userId, source, e.getMessage());
        }
    }

    private String username(Long userId) {
        if (userId == null) return "Someone";
        return userRepository.findById(userId).map(u -> u.getUsername()).orElse("Someone");
    }
}
