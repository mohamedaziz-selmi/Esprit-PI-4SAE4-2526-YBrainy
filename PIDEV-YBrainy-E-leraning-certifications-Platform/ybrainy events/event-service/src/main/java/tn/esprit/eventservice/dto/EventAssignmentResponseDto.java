package tn.esprit.eventservice.dto;

public record EventAssignmentResponseDto(
        long eventId,
        long studentId,
        String inscriptionStatus
){}