package tn.esprit.tpfoyer.Config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_ROUTING_KEY = "payment.completed";

    public static final String ENROLLMENT_EXCHANGE = "enrollment.exchange";
    public static final String ENROLLMENT_COMPLETED_QUEUE = "enrollment.completed.queue";
    public static final String ENROLLMENT_COMPLETED_KEY = "enrollment.completed";

    public static final String COURSE_EXCHANGE = "course.exchange";
    public static final String COURSE_DELETED_QUEUE = "course.deleted.queue";
    public static final String COURSE_DELETED_KEY = "course.deleted";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange enrollmentExchange() {
        return new TopicExchange(ENROLLMENT_EXCHANGE);
    }

    @Bean
    public Queue enrollmentCompletedQueue() {
        return QueueBuilder.durable(ENROLLMENT_COMPLETED_QUEUE).build();
    }

    @Bean
    public Binding enrollmentCompletedBinding() {
        return BindingBuilder.bind(enrollmentCompletedQueue())
            .to(enrollmentExchange())
            .with(ENROLLMENT_COMPLETED_KEY);
    }

    @Bean
    public TopicExchange courseExchange() {
        return new TopicExchange(COURSE_EXCHANGE);
    }

    @Bean
    public Queue courseDeletedQueue() {
        return QueueBuilder.durable(COURSE_DELETED_QUEUE).build();
    }

    @Bean
    public Binding courseDeletedBinding() {
        return BindingBuilder.bind(courseDeletedQueue())
            .to(courseExchange())
            .with(COURSE_DELETED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
