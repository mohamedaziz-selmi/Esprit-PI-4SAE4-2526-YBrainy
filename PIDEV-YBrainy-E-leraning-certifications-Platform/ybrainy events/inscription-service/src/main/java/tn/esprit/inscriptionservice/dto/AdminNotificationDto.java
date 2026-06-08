package tn.esprit.inscriptionservice.dto;

public record AdminNotificationDto(
        long idNotification,
        String type,
        String title,
        String message,
        long eventId,
        long studentId,
        String createdAt
) {}
