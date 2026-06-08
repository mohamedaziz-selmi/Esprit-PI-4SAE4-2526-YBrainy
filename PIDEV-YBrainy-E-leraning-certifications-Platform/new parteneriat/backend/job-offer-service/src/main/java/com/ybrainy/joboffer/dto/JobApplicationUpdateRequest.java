package com.ybrainy.joboffer.dto;

import com.ybrainy.joboffer.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobApplicationUpdateRequest(
    @NotNull ApplicationStatus status,
    @Size(max = 2000) String reviewerNotes) {
}

