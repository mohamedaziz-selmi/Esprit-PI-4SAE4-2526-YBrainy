package tn.esprit.eventservice.dto;

public record ImageGenerationResponse(
        String imageUrl,
        boolean generatedByAi
) {}
