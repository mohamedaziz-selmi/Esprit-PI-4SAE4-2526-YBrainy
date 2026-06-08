package com.ybrainy.joboffer.dto;

import com.ybrainy.joboffer.entity.ApplicationStatus;
import java.time.Instant;

public record JobApplicationResponse(
    String id,
    String offerId,
    String applicantName,
    String applicantEmail,
    String message,
    String cvDataUrl,
    ApplicationStatus status,
    String reviewerNotes,
    Instant createdAt) {
}
