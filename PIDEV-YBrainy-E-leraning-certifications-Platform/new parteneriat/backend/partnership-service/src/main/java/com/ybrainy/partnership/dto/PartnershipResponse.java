package com.ybrainy.partnership.dto;

import java.time.Instant;

public record PartnershipResponse(
    String id,
    String name,
    String email,
    String phone,
    String website,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
