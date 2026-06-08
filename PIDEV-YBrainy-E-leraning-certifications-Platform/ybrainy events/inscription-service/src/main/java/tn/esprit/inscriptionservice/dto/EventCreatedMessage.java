package tn.esprit.inscriptionservice.dto;

import java.time.LocalDateTime;

public record EventCreatedMessage(
        long eventId,
        String name,
        String referenceEvent,
        String location,
        int capacite,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        long adminId
) {
}
