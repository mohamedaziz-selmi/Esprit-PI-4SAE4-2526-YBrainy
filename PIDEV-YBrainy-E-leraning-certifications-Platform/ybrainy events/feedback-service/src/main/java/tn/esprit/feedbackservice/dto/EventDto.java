package tn.esprit.feedbackservice.dto;

/**
 * Lightweight event representation received from event-service via Feign.
 * Only the fields needed by feedback-service are mapped.
 */
public record EventDto(
        long   idEvent,
        String name,
        String statut   // "PUBLIE" | "ANNULE" | "TERMINE"
) {}
