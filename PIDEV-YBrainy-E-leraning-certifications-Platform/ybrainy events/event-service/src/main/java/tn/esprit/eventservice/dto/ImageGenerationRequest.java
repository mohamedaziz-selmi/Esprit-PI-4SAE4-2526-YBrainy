package tn.esprit.eventservice.dto;

public record ImageGenerationRequest(
        String name,
        String description,
        String type
) {}
