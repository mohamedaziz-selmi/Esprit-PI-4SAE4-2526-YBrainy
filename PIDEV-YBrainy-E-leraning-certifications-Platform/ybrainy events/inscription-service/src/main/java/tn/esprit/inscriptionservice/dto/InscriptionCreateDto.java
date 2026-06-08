package tn.esprit.inscriptionservice.dto;

/** Payload received when event-service asks to create an inscription. */
public record InscriptionCreateDto(
        long eventId,
        long studentId,
        String initialStatus
) {}
