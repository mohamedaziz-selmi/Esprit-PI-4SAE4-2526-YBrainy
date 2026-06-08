package com.ybrainy.joboffer.messaging;

import com.ybrainy.joboffer.entity.JobApplication;
import java.time.Instant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JobApplicationEventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final String exchange;
  private final String routingKey;

  public JobApplicationEventPublisher(
      RabbitTemplate rabbitTemplate,
      @Value("${app.rabbitmq.exchange}") String exchange,
      @Value("${app.rabbitmq.application.routing-key}") String routingKey) {
    this.rabbitTemplate = rabbitTemplate;
    this.exchange = exchange;
    this.routingKey = routingKey;
  }

  public void publishCreated(JobApplication application, String offerTitle) {
    publish("APPLICATION_CREATED", application, offerTitle);
  }

  public void publishStatusChanged(JobApplication application, String offerTitle) {
    publish("APPLICATION_STATUS_CHANGED", application, offerTitle);
  }

  private void publish(String eventType, JobApplication application, String offerTitle) {
    rabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        new JobApplicationEvent(
            eventType,
            application.getId(),
            application.getOfferId(),
            offerTitle,
            application.getApplicantName(),
            application.getApplicantEmail(),
            application.getStatus(),
            Instant.now()));
  }
}
