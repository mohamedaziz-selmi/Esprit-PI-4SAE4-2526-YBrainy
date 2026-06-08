package tn.esprit.eventservice.dto;

public record InscriptionAssignmentResultDto(
        long idInscription,
        long eventId,
        long studentId,
        String statut
) {}
