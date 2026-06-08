package com.ybrainy.joboffer.messaging;

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
  Queue partnershipEventsQueue(@Value("${app.rabbitmq.partnership.queue}") String queueName) {
    return new Queue(queueName, true);
  }

  @Bean
  Queue jobApplicationEventsQueue(@Value("${app.rabbitmq.application.queue}") String queueName) {
    return new Queue(queueName, true);
  }

  @Bean
  Queue jobApplicationAnalyticsQueue(@Value("${app.rabbitmq.application.analytics-queue}") String queueName) {
    return new Queue(queueName, true);
  }

  @Bean
  Binding partnershipBinding(
      Queue partnershipEventsQueue,
      TopicExchange appExchange,
      @Value("${app.rabbitmq.partnership.routing-key}") String routingKey) {
    return BindingBuilder.bind(partnershipEventsQueue).to(appExchange).with(routingKey);
  }

  @Bean
  Binding jobApplicationBinding(
      Queue jobApplicationEventsQueue,
      TopicExchange appExchange,
      @Value("${app.rabbitmq.application.routing-key}") String routingKey) {
    return BindingBuilder.bind(jobApplicationEventsQueue).to(appExchange).with(routingKey);
  }

  @Bean
  Binding jobApplicationAnalyticsBinding(
      Queue jobApplicationAnalyticsQueue,
      TopicExchange appExchange,
      @Value("${app.rabbitmq.application.routing-key}") String routingKey) {
    return BindingBuilder.bind(jobApplicationAnalyticsQueue).to(appExchange).with(routingKey);
  }

  @Bean
  MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }
}
