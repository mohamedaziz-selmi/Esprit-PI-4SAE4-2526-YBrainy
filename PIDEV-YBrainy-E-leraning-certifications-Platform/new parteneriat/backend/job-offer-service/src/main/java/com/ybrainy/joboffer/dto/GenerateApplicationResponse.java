package com.ybrainy.joboffer.dto;

public record GenerateApplicationResponse(
    String optimizedCV,
    String coverLetter,
    String professionalProfilePhotoDataUrl,
    String profilePhotoAssistantMessage) {}

