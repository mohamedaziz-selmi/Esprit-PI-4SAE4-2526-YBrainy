package com.ybrainy.joboffer.service;

import com.ybrainy.joboffer.dto.ProfessionalPhotoOutcome;

public interface GeminiService {

  String generateOptimizedCV(String cv, String jobDescription, String cvSkeleton);

  String generateCoverLetter(String cv, String jobDescription);

  /**
   * Tente une version professionnelle de la photo (modele image Gemini si disponible), sinon conseils
   * vision en francais.
   */
  ProfessionalPhotoOutcome enhanceProfilePhoto(byte[] imageBytes, String mimeType);
}
