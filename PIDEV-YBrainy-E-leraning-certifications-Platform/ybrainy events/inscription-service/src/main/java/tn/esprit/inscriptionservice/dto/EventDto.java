package tn.esprit.inscriptionservice.dto;

import java.time.LocalDateTime;
/** Lightweight event representation received from event-service. */
public record EventDto(
        long idEvent,
        String name,
        String referenceEvent,
        String description,
        String location,
        int capacite,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        String type,
        String statut
) {}
