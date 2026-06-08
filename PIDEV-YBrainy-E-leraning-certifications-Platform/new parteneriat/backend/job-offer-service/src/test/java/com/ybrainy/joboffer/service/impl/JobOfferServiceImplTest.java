package com.ybrainy.joboffer.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ybrainy.joboffer.client.PartnershipClient;
import com.ybrainy.joboffer.dto.ExistsResponse;
import com.ybrainy.joboffer.dto.JobOfferRequest;
import com.ybrainy.joboffer.dto.JobOfferResponse;
import com.ybrainy.joboffer.entity.ContractType;
import com.ybrainy.joboffer.entity.JobOffer;
import com.ybrainy.joboffer.entity.OfferStatus;
import com.ybrainy.joboffer.exception.BusinessException;
import com.ybrainy.joboffer.exception.ResourceNotFoundException;
import com.ybrainy.joboffer.mapper.JobOfferMapper;
import com.ybrainy.joboffer.repository.JobOfferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobOfferServiceImplTest {

  @Mock private JobOfferRepository repository;
  @Mock private JobOfferMapper mapper;
  @Mock private PartnershipClient partnershipClient;

  private JobOfferServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new JobOfferServiceImpl(repository, mapper, partnershipClient);
  }

  @Test
  void create_whenValidRequest_savesAndReturnsResponse() {
    JobOfferRequest request = requestWithSalaries("partner-1", new BigDecimal("1000"), new BigDecimal("1500"));
    JobOffer entity = new JobOffer();
    entity.setId("offer-1");
    JobOfferResponse response = responseFor(entity.getId());

    when(partnershipClient.exists("partner-1")).thenReturn(new ExistsResponse(true));
    when(mapper.toEntity(request)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(eq(entity), any(), any())).thenReturn(response);

    JobOfferResponse result = service.create(request);

    assertEquals(response, result);
    verify(partnershipClient).exists("partner-1");
    verify(mapper).toEntity(request);
    verify(repository).save(entity);
    verify(mapper).toResponse(eq(entity), any(), any());
  }

  @Test
  void create_whenPartnershipServiceFails_throwsBusinessException() {
    JobOfferRequest request = requestWithSalaries("partner-1", new BigDecimal("1000"), new BigDecimal("1500"));
    when(partnershipClient.exists("partner-1")).thenThrow(new RuntimeException("down"));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

    assertTrue(ex.getMessage().contains("Unable to validate partnership"));
    verifyNoInteractions(mapper);
    verify(repository, never()).save(any());
  }

  @Test
  void create_whenPartnershipDoesNotExist_throwsBusinessException() {
    JobOfferRequest request = requestWithSalaries("partner-1", new BigDecimal("1000"), new BigDecimal("1500"));
    when(partnershipClient.exists("partner-1")).thenReturn(new ExistsResponse(false));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

    assertTrue(ex.getMessage().contains("Invalid partnershipId"));
    verifyNoInteractions(mapper);
    verify(repository, never()).save(any());
  }

  @Test
  void create_whenSalaryMinGreaterThanMax_throwsBusinessException() {
    JobOfferRequest request = requestWithSalaries("partner-1", new BigDecimal("2000"), new BigDecimal("1500"));
    when(partnershipClient.exists("partner-1")).thenReturn(new ExistsResponse(true));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

    assertTrue(ex.getMessage().contains("salaryMin"));
    verify(mapper, never()).toEntity(any());
    verify(repository, never()).save(any());
  }

  @Test
  void update_whenOfferNotFound_throwsResourceNotFoundException() {
    JobOfferRequest request = requestWithSalaries("partner-1", new BigDecimal("1000"), new BigDecimal("1500"));
    when(partnershipClient.exists("partner-1")).thenReturn(new ExistsResponse(true));
    when(repository.findById("missing-id")).thenReturn(Optional.empty());

    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> service.update("missing-id", request));

    assertTrue(ex.getMessage().contains("Job offer not found"));
    verify(mapper, never()).apply(any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void getAll_whenKeywordProvided_usesKeywordRepositorySearch() {
    Pageable pageable = PageRequest.of(0, 10);
    JobOffer entity = new JobOffer();
    entity.setId("offer-1");
    JobOfferResponse response = responseFor("offer-1");

    when(repository.findByTitleContainingIgnoreCase("java", pageable))
        .thenReturn(new PageImpl<>(List.of(entity)));
    when(mapper.toResponse(eq(entity), any(), any())).thenReturn(response);

    Page<JobOfferResponse> page = service.getAll(null, null, "  java  ", pageable);

    assertEquals(1, page.getTotalElements());
    assertEquals(response, page.getContent().get(0));
    verify(repository).findByTitleContainingIgnoreCase("java", pageable);
    verify(repository, never()).findAll(any(Pageable.class));
  }

  @Test
  void delete_whenOfferExists_deletesEntity() {
    JobOffer entity = new JobOffer();
    entity.setId("offer-1");
    when(repository.findById("offer-1")).thenReturn(Optional.of(entity));

    service.delete("offer-1");

    verify(repository).delete(eq(entity));
  }

  private static JobOfferRequest requestWithSalaries(
      String partnershipId, BigDecimal salaryMin, BigDecimal salaryMax) {
    return new JobOfferRequest(
        "Java Developer",
        "Build and maintain backend services.",
        "Tunis",
        null,
        salaryMin,
        salaryMax,
        ContractType.CDI,
        OfferStatus.OPEN,
        LocalDate.now().plusDays(5),
        partnershipId);
  }

  private static JobOfferResponse responseFor(String id) {
    return new JobOfferResponse(
        id,
        "Java Developer",
        "Build and maintain backend services.",
        "Tunis",
        null,
        new BigDecimal("1000"),
        new BigDecimal("1500"),
        ContractType.CDI,
        OfferStatus.OPEN,
        LocalDate.now().plusDays(5),
        "partner-1",
        "Partner Name",
        "partner@example.com",
        Instant.now(),
        Instant.now());
  }
}

