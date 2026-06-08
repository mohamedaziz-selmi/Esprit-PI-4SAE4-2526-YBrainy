package com.ybrainy.joboffer.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class JobApplicationAnalyticsListener {

  private static final Logger log = LoggerFactory.getLogger(JobApplicationAnalyticsListener.class);

  @RabbitListener(queues = "${app.rabbitmq.application.analytics-queue}")
  public void handle(JobApplicationEvent event) {
    if (event == null) {
      log.warn("Received null job application analytics event");
      return;
    }

    log.info(
        "[ANALYTICS] eventType={}, applicationId={}, offerId={}, applicantEmail={}, status={}, occurredAt={}",
        event.eventType(),
        event.applicationId(),
        event.offerId(),
        event.applicantEmail(),
        event.status(),
        event.occurredAt());
  }
}
