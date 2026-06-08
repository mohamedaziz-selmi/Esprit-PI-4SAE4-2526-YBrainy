package com.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.finance-payment-queue}")
    private String financePaymentQueue;

    @Value("${app.rabbitmq.payment-completed-routing-key}")
    private String paymentCompletedRoutingKey;

    @Bean
    public TopicExchange ybrainyEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue financePaymentQueue() {
        return QueueBuilder.durable(financePaymentQueue).build();
    }

    @Bean
    public Binding financePaymentBinding() {
        return BindingBuilder.bind(financePaymentQueue()).to(ybrainyEventsExchange()).with(paymentCompletedRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}