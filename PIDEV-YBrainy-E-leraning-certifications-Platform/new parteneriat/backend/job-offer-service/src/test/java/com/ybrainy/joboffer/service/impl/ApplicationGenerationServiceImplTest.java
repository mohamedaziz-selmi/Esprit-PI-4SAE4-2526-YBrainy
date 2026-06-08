package com.ybrainy.joboffer.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ybrainy.joboffer.dto.GenerateApplicationRequest;
import com.ybrainy.joboffer.dto.GenerateApplicationResponse;
import com.ybrainy.joboffer.dto.ProfessionalPhotoOutcome;
import com.ybrainy.joboffer.exception.BusinessException;
import com.ybrainy.joboffer.exception.ExternalServiceException;
import com.ybrainy.joboffer.service.GeminiService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationGenerationServiceImplTest {

  @Mock private GeminiService geminiService;
  @Captor private ArgumentCaptor<byte[]> imageBytesCaptor;

  private ApplicationGenerationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ApplicationGenerationServiceImpl(geminiService);
  }

  @Test
  void generate_withoutProfileImage_returnsCvAndCoverLetter() {
    GenerateApplicationRequest request =
        new GenerateApplicationRequest("  raw cv  ", "  job description  ", "template", null, null);

    when(geminiService.generateOptimizedCV("raw cv", "job description", "template")).thenReturn("optimized cv");
    when(geminiService.generateCoverLetter("raw cv", "job description")).thenReturn("cover letter");

    GenerateApplicationResponse response = service.generate(request);

    assertEquals("optimized cv", response.optimizedCV());
    assertEquals("cover letter", response.coverLetter());
    assertNull(response.professionalProfilePhotoDataUrl());
    assertNull(response.profilePhotoAssistantMessage());
    verify(geminiService, never()).enhanceProfilePhoto(any(), anyString());
  }

  @Test
  void generate_withProfileImage_decodesAndSendsToGemini() {
    byte[] original = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    String b64 = Base64.getEncoder().encodeToString(original);
    GenerateApplicationRequest request =
        new GenerateApplicationRequest("cv", "job", null, b64, "image/png");

    when(geminiService.generateOptimizedCV("cv", "job", null)).thenReturn("optimized");
    when(geminiService.generateCoverLetter("cv", "job")).thenReturn("letter");
    when(geminiService.enhanceProfilePhoto(any(), eq("image/png")))
        .thenReturn(new ProfessionalPhotoOutcome("data:image/png;base64,abc", "photo ok"));

    GenerateApplicationResponse response = service.generate(request);

    verify(geminiService).enhanceProfilePhoto(imageBytesCaptor.capture(), eq("image/png"));
    assertArrayEquals(original, imageBytesCaptor.getValue());
    assertEquals("data:image/png;base64,abc", response.professionalProfilePhotoDataUrl());
    assertEquals("photo ok", response.profilePhotoAssistantMessage());
  }

  @Test
  void generate_withUnknownMime_usesJpegAsDefault() {
    byte[] original = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    String b64 = Base64.getEncoder().encodeToString(original);
    GenerateApplicationRequest request =
        new GenerateApplicationRequest("cv", "job", null, b64, "application/octet-stream");

    when(geminiService.generateOptimizedCV("cv", "job", null)).thenReturn("optimized");
    when(geminiService.generateCoverLetter("cv", "job")).thenReturn("letter");
    when(geminiService.enhanceProfilePhoto(any(), anyString()))
        .thenReturn(ProfessionalPhotoOutcome.empty());

    service.generate(request);

    verify(geminiService).enhanceProfilePhoto(any(), eq("image/jpeg"));
  }

  @Test
  void generate_withInvalidBase64_throwsBusinessException() {
    GenerateApplicationRequest request =
        new GenerateApplicationRequest("cv", "job", null, "%%%%invalid-base64%%%%", "image/jpeg");

    BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(request));
    assertTrue(ex.getMessage().contains("invalid"));
    verifyNoInteractions(geminiService);
  }

  @Test
  void generate_withTooLongCv_throwsBusinessException() {
    String tooLongCv = "a".repeat(120_001);
    GenerateApplicationRequest request =
        new GenerateApplicationRequest(tooLongCv, "job", null, null, null);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.generate(request));
    assertTrue(ex.getMessage().contains("CV is too long"));
    verifyNoInteractions(geminiService);
  }

  @Test
  void generate_whenProviderReturnsBlankContent_throwsExternalServiceException() {
    GenerateApplicationRequest request = new GenerateApplicationRequest("cv", "job", null, null, null);

    when(geminiService.generateOptimizedCV("cv", "job", null)).thenReturn("   ");
    when(geminiService.generateCoverLetter("cv", "job")).thenReturn("letter");

    ExternalServiceException ex =
        assertThrows(ExternalServiceException.class, () -> service.generate(request));
    assertTrue(ex.getMessage().contains("incomplete content"));
  }
}

