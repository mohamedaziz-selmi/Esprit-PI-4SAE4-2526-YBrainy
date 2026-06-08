package com.ybrainy.joboffer.dto;

/** Resultat du traitement photo (image generee et/ou message d accompagnement). */
public record ProfessionalPhotoOutcome(String professionalProfilePhotoDataUrl, String profilePhotoAssistantMessage) {

  public static ProfessionalPhotoOutcome empty() {
    return new ProfessionalPhotoOutcome(null, null);
  }
}
