package com.ybrainy.joboffer.messaging;

import com.ybrainy.joboffer.entity.ApplicationStatus;
import java.time.Instant;

public record JobApplicationEvent(
    String eventType,
    String applicationId,
    String offerId,
    String offerTitle,
    String applicantName,
    String applicantEmail,
    ApplicationStatus status,
    Instant occurredAt) {
}
