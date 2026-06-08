package com.ybrainy.joboffer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobApplicationRequest(
    @NotBlank @Size(max = 160) String applicantName,
    @NotBlank @Email @Size(max = 200) String applicantEmail,
    @Size(max = 4000) String message,
    String cvDataUrl) {
}
