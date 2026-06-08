package com.ybrainy.joboffer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateApplicationRequest(
    @NotBlank @Size(max = 1000000) String cv,
    @NotBlank @Size(max = 1000000) String jobDescription,
    @Size(max = 50000) String cvSkeleton,
    /** Image de profil optionnelle (base64 sans prefixe data:) */
    @Size(max = 12_000_000) String profileImageBase64,
    /** ex. image/jpeg, image/png, image/webp */
    @Size(max = 64) String profileImageMimeType) {}
