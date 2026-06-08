package com.esprit.userservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "forum.events";

    public static final String QUEUE_THREAD_CREATED  = "user.thread.created";
    public static final String QUEUE_POST_CREATED    = "user.post.created";
    public static final String QUEUE_COMMENT_CREATED = "user.comment.created";
    public static final String QUEUE_UPVOTE_RECEIVED = "user.upvote.received";
    public static final String QUEUE_BEST_ANSWER     = "user.best.answer";

    public static final String RK_THREAD_CREATED  = "thread.created";
    public static final String RK_POST_CREATED    = "post.created";
    public static final String RK_COMMENT_CREATED = "comment.created";
    public static final String RK_UPVOTE_RECEIVED = "upvote.received";
    public static final String RK_BEST_ANSWER     = "best.answer";

    @Bean
    public TopicExchange forumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean public Queue threadCreatedQueue()  { return new Queue(QUEUE_THREAD_CREATED,  true); }
    @Bean public Queue postCreatedQueue()    { return new Queue(QUEUE_POST_CREATED,    true); }
    @Bean public Queue commentCreatedQueue() { return new Queue(QUEUE_COMMENT_CREATED, true); }
    @Bean public Queue upvoteReceivedQueue() { return new Queue(QUEUE_UPVOTE_RECEIVED, true); }
    @Bean public Queue bestAnswerQueue()     { return new Queue(QUEUE_BEST_ANSWER,     true); }

    @Bean public Binding bindThreadCreated(Queue threadCreatedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(threadCreatedQueue).to(forumExchange).with(RK_THREAD_CREATED);
    }
    @Bean public Binding bindPostCreated(Queue postCreatedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(postCreatedQueue).to(forumExchange).with(RK_POST_CREATED);
    }
    @Bean public Binding bindCommentCreated(Queue commentCreatedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(commentCreatedQueue).to(forumExchange).with(RK_COMMENT_CREATED);
    }
    @Bean public Binding bindUpvoteReceived(Queue upvoteReceivedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(upvoteReceivedQueue).to(forumExchange).with(RK_UPVOTE_RECEIVED);
    }
    @Bean public Binding bindBestAnswer(Queue bestAnswerQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(bestAnswerQueue).to(forumExchange).with(RK_BEST_ANSWER);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
