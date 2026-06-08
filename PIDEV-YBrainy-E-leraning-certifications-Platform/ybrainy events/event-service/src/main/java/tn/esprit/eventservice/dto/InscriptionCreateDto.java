package tn.esprit.eventservice.dto;

/**
 * Payload sent to inscription-service when assigning a student to an event.
 */
public record InscriptionCreateDto(
        long eventId,
        long studentId,
        String initialStatus
) {}
