package com.ybrainy.joboffer.messaging;

import com.ybrainy.joboffer.entity.JobApplication;
import com.ybrainy.joboffer.entity.ApplicationStatus;
import com.ybrainy.joboffer.repository.JobApplicationRepository;
import com.ybrainy.joboffer.service.JobApplicationNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class JobApplicationEventListener {

  private static final Logger log = LoggerFactory.getLogger(JobApplicationEventListener.class);

  private final JobApplicationRepository jobApplicationRepository;
  private final JobApplicationNotificationService notificationService;

  public JobApplicationEventListener(
      JobApplicationRepository jobApplicationRepository,
      JobApplicationNotificationService notificationService) {
    this.jobApplicationRepository = jobApplicationRepository;
    this.notificationService = notificationService;
  }

  @RabbitListener(queues = "${app.rabbitmq.application.queue}")
  public void handleJobApplicationEvent(JobApplicationEvent event) {
    if (event == null || event.applicationId() == null || event.applicationId().isBlank()) {
      log.warn("Skipping invalid job application event: {}", event);
      return;
    }

    if ("APPLICATION_CREATED".equals(event.eventType())) {
      log.info("Received new application event for application {}", event.applicationId());
      return;
    }

    if (!"APPLICATION_STATUS_CHANGED".equals(event.eventType()) || event.status() != ApplicationStatus.ACCEPTED) {
      log.info("Received job application event {} for application {}", event.eventType(), event.applicationId());
      return;
    }

    JobApplication application =
        jobApplicationRepository.findById(event.applicationId()).orElse(null);
    if (application == null) {
      log.warn("Application {} no longer exists. Skipping notification.", event.applicationId());
      return;
    }

    boolean notificationSent = notificationService.notifyAccepted(application, event.offerTitle());
    if (!notificationSent) {
      log.warn("Accepted application event processed but no notification was sent for {}", event.applicationId());
    }
  }
}
