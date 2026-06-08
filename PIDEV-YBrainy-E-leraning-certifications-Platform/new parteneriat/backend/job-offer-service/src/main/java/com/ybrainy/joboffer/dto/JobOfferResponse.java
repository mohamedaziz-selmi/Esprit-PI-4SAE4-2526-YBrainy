package com.ybrainy.joboffer.dto;

import com.ybrainy.joboffer.entity.ContractType;
import com.ybrainy.joboffer.entity.OfferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record JobOfferResponse(
    String id,
    String title,
    String description,
    String location,
    String imageDataUrl,
    BigDecimal salaryMin,
    BigDecimal salaryMax,
    ContractType contractType,
    OfferStatus status,
    LocalDate deadline,
    String partnershipId,
    String partnershipName,
    String partnershipEmail,
    Instant createdAt,
    Instant updatedAt) {
}
