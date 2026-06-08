package com.ybrainy.partnership.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

  @Bean
  TopicExchange appExchange(@Value("${app.rabbitmq.exchange}") String exchangeName) {
    return new TopicExchange(exchangeName, true, false);
  }

  @Bean
  Queue partnershipQueue(@Value("${app.rabbitmq.partnership.queue}") String queueName) {
    return new Queue(queueName, true);
  }

  @Bean
  Binding partnershipBinding(
      Queue partnershipQueue,
      TopicExchange appExchange,
      @Value("${app.rabbitmq.partnership.routing-key}") String routingKey) {
    return BindingBuilder.bind(partnershipQueue).to(appExchange).with(routingKey);
  }

  @Bean
  MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }
}
