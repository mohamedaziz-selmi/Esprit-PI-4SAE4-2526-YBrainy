package com.ybrainy.joboffer.messaging;

import java.time.Instant;

public record PartnershipEvent(
    String eventType,
    String partnershipId,
    String name,
    String email,
    boolean active,
    Instant occurredAt) {
}
