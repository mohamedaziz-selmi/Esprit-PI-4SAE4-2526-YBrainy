package tn.esprit.eventservice.dto;

public record DescriptionGenerationResponse(
        String description,
        boolean generatedByAi
) {}
