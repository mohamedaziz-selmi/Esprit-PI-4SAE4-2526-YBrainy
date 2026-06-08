package com.ybrainy.partnership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnershipRequest(
    @NotBlank @Size(max = 140) String name,
    @NotBlank @Email @Size(max = 140) String email,
    @Size(max = 30) String phone,
    @Size(max = 255) String website,
    @Size(max = 2000) String description,
    boolean active) {
}
