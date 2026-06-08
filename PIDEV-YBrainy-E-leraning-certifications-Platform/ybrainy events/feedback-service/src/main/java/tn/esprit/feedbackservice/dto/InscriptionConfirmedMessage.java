package tn.esprit.feedbackservice.dto;

import java.time.LocalDateTime;

public record InscriptionConfirmedMessage(
        long inscriptionId,
        long eventId,
        long studentId,
        String statut,
        LocalDateTime confirmedAt,
        String source
) {
}
