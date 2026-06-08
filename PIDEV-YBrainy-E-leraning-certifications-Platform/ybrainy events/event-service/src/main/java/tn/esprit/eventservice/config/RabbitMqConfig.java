package tn.esprit.eventservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENT_EXCHANGE = "ybrainy.event.exchange";
    public static final String EVENT_CREATED_QUEUE = "ybrainy.event.created.queue";
    public static final String EVENT_CREATED_ROUTING_KEY = "event.created";

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EVENT_EXCHANGE);
    }

    @Bean
    public Queue eventCreatedQueue() {
        return new Queue(EVENT_CREATED_QUEUE, true);
    }

    @Bean
    public Binding eventCreatedBinding(Queue eventCreatedQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(eventCreatedQueue).to(eventExchange).with(EVENT_CREATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
