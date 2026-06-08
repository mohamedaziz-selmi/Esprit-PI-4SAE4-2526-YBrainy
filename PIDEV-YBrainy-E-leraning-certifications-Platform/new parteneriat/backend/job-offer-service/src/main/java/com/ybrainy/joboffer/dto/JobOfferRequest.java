package com.ybrainy.joboffer.dto;

import com.ybrainy.joboffer.entity.ContractType;
import com.ybrainy.joboffer.entity.OfferStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record JobOfferRequest(
    @NotBlank @Size(max = 180) String title,
    @NotBlank @Size(max = 4000) String description,
    @Size(max = 180) String location,
    String imageDataUrl,
    @DecimalMin(value = "0.0") BigDecimal salaryMin,
    @DecimalMin(value = "0.0") BigDecimal salaryMax,
    @NotNull ContractType contractType,
    @NotNull OfferStatus status,
    @FutureOrPresent LocalDate deadline,
    @NotBlank String partnershipId) {
}
