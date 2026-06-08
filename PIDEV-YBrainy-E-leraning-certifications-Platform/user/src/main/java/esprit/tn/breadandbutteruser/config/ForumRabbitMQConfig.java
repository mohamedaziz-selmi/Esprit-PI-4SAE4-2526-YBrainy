package esprit.tn.breadandbutteruser.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the queues and bindings that receive forum events
 * (thread created, post created, comment, upvote, best answer).
 * Queue names match those used by the forum services' publishers.
 */
@Configuration
public class ForumRabbitMQConfig {

    public static final String EXCHANGE = "forum.events";

    public static final String QUEUE_THREAD_CREATED  = "user.thread.created";
    public static final String QUEUE_POST_CREATED    = "user.post.created";
    public static final String QUEUE_COMMENT_CREATED = "user.comment.created";
    public static final String QUEUE_UPVOTE_RECEIVED = "user.upvote.received";
    public static final String QUEUE_BEST_ANSWER     = "user.best.answer";

    @Bean
    public TopicExchange forumEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean public Queue forumThreadCreatedQueue()  { return new Queue(QUEUE_THREAD_CREATED,  true); }
    @Bean public Queue forumPostCreatedQueue()    { return new Queue(QUEUE_POST_CREATED,    true); }
    @Bean public Queue forumCommentCreatedQueue() { return new Queue(QUEUE_COMMENT_CREATED, true); }
    @Bean public Queue forumUpvoteReceivedQueue() { return new Queue(QUEUE_UPVOTE_RECEIVED, true); }
    @Bean public Queue forumBestAnswerQueue()     { return new Queue(QUEUE_BEST_ANSWER,     true); }

    @Bean public Binding bindForumThreadCreated(Queue forumThreadCreatedQueue, TopicExchange forumEventsExchange) {
        return BindingBuilder.bind(forumThreadCreatedQueue).to(forumEventsExchange).with("thread.created");
    }
    @Bean public Binding bindForumPostCreated(Queue forumPostCreatedQueue, TopicExchange forumEventsExchange) {
        return BindingBuilder.bind(forumPostCreatedQueue).to(forumEventsExchange).with("post.created");
    }
    @Bean public Binding bindForumCommentCreated(Queue forumCommentCreatedQueue, TopicExchange forumEventsExchange) {
        return BindingBuilder.bind(forumCommentCreatedQueue).to(forumEventsExchange).with("comment.created");
    }
    @Bean public Binding bindForumUpvoteReceived(Queue forumUpvoteReceivedQueue, TopicExchange forumEventsExchange) {
        return BindingBuilder.bind(forumUpvoteReceivedQueue).to(forumEventsExchange).with("upvote.received");
    }
    @Bean public Binding bindForumBestAnswer(Queue forumBestAnswerQueue, TopicExchange forumEventsExchange) {
        return BindingBuilder.bind(forumBestAnswerQueue).to(forumEventsExchange).with("best.answer");
    }
}
