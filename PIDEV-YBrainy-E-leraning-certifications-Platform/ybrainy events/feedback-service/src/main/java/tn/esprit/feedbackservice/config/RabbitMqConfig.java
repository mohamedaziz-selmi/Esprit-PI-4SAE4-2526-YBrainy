package tn.esprit.feedbackservice.config;

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

    public static final String INSCRIPTION_EXCHANGE = "ybrainy.inscription.exchange";
    public static final String INSCRIPTION_CONFIRMED_QUEUE = "ybrainy.inscription.confirmed.queue";
    public static final String INSCRIPTION_CONFIRMED_ROUTING_KEY = "inscription.confirmed";

    @Bean
    public TopicExchange inscriptionExchange() {
        return new TopicExchange(INSCRIPTION_EXCHANGE);
    }

    @Bean
    public Queue inscriptionConfirmedQueue() {
        return new Queue(INSCRIPTION_CONFIRMED_QUEUE, true);
    }

    @Bean
    public Binding inscriptionConfirmedBinding(Queue inscriptionConfirmedQueue, TopicExchange inscriptionExchange) {
        return BindingBuilder.bind(inscriptionConfirmedQueue).to(inscriptionExchange).with(INSCRIPTION_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
