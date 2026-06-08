package com.ybrainy.partnership.dto;

import com.ybrainy.partnership.entity.Partnership;
import java.time.Instant;
import java.time.LocalDate;

public record JobOfferSummary(
    String id,
    String title,
    String description,
    String location,
    String imageDataUrl,
    LocalDate deadline,
    Instant createdAt,
    Instant updatedAt,
    String partnershipId,
    String partnershipName,
    String partnershipEmail) {
}

