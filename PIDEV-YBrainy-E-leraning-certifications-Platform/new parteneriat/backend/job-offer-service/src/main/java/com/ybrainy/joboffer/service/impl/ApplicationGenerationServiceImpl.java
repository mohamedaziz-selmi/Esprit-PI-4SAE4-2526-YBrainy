package com.ybrainy.joboffer.service.impl;

import com.ybrainy.joboffer.dto.GenerateApplicationRequest;
import com.ybrainy.joboffer.dto.GenerateApplicationResponse;
import com.ybrainy.joboffer.dto.ProfessionalPhotoOutcome;
import com.ybrainy.joboffer.exception.BusinessException;
import com.ybrainy.joboffer.exception.ExternalServiceException;
import com.ybrainy.joboffer.service.ApplicationGenerationService;
import com.ybrainy.joboffer.service.GeminiService;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class ApplicationGenerationServiceImpl implements ApplicationGenerationService {

  private static final int MAX_CV_LENGTH = 120_000;
  private static final int MAX_JOB_DESCRIPTION_LENGTH = 120_000;
  private static final int MAX_PROFILE_IMAGE_BYTES = 5 * 1024 * 1024;

  private final GeminiService geminiService;

  public ApplicationGenerationServiceImpl(GeminiService geminiService) {
    this.geminiService = geminiService;
  }

  @Override
  public GenerateApplicationResponse generate(GenerateApplicationRequest request) {
    String cv = request.cv().trim();
    String jobDescription = request.jobDescription().trim();
    validateInputSize(cv, jobDescription);

    final byte[] profileBytes = decodeProfileIfPresent(request);
    final String profileMime = normalizeProfileMime(request.profileImageMimeType());

    String optimizedCV = geminiService.generateOptimizedCV(cv, jobDescription, request.cvSkeleton());
    String coverLetter = geminiService.generateCoverLetter(cv, jobDescription);
    ProfessionalPhotoOutcome photo =
        profileBytes == null
            ? ProfessionalPhotoOutcome.empty()
            : geminiService.enhanceProfilePhoto(profileBytes, profileMime);

    if (optimizedCV.isBlank() || coverLetter.isBlank()) {
      throw new ExternalServiceException("AI provider returned incomplete content.");
    }
    return new GenerateApplicationResponse(
        optimizedCV,
        coverLetter,
        photo.professionalProfilePhotoDataUrl(),
        photo.profilePhotoAssistantMessage());
  }

  private byte[] decodeProfileIfPresent(GenerateApplicationRequest request) {
    if (request.profileImageBase64() == null || request.profileImageBase64().isBlank()) {
      return null;
    }
    String cleaned = request.profileImageBase64().replaceAll("\\s", "");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(cleaned);
    } catch (IllegalArgumentException e) {
      throw new BusinessException("Profile image base64 is invalid.");
    }
    if (decoded.length > MAX_PROFILE_IMAGE_BYTES) {
      throw new BusinessException("Profile image exceeds maximum size (5MB).");
    }
    return decoded;
  }

  private static String normalizeProfileMime(String mime) {
    if (mime == null || mime.isBlank()) {
      return "image/jpeg";
    }
    String m = mime.trim().toLowerCase();
    if ("image/jpg".equals(m)) {
      return "image/jpeg";
    }
    if ("image/jpeg".equals(m) || "image/png".equals(m) || "image/webp".equals(m)) {
      return m;
    }
    return "image/jpeg";
  }

  private void validateInputSize(String cv, String jobDescription) {
    if (cv.length() > MAX_CV_LENGTH) {
      throw new BusinessException(
          "CV is too long for AI processing. Please keep it under " + MAX_CV_LENGTH + " characters.");
    }
    if (jobDescription.length() > MAX_JOB_DESCRIPTION_LENGTH) {
      throw new BusinessException(
          "Job description is too long for AI processing. Please keep it under "
              + MAX_JOB_DESCRIPTION_LENGTH
              + " characters.");
    }
  }
}
