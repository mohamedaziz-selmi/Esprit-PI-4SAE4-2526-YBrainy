package com.ybrainy.partnership.messaging;

import com.ybrainy.partnership.entity.Partnership;
import java.time.Instant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PartnershipEventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final String exchange;
  private final String routingKey;

  public PartnershipEventPublisher(
      RabbitTemplate rabbitTemplate,
      @Value("${app.rabbitmq.exchange}") String exchange,
      @Value("${app.rabbitmq.partnership.routing-key}") String routingKey) {
    this.rabbitTemplate = rabbitTemplate;
    this.exchange = exchange;
    this.routingKey = routingKey;
  }

  public void publishCreated(Partnership partnership) {
    publish("PARTNERSHIP_CREATED", partnership);
  }

  public void publishUpdated(Partnership partnership) {
    publish("PARTNERSHIP_UPDATED", partnership);
  }

  public void publishDeleted(Partnership partnership) {
    publish("PARTNERSHIP_DELETED", partnership);
  }

  private void publish(String eventType, Partnership partnership) {
    rabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        new PartnershipEvent(
            eventType,
            partnership.getId(),
            partnership.getName(),
            partnership.getEmail(),
            partnership.isActive(),
            Instant.now()));
  }
}
