package com.ybrainy.joboffer.service.impl;

import com.ybrainy.joboffer.dto.JobApplicationRequest;
import com.ybrainy.joboffer.dto.JobApplicationResponse;
import com.ybrainy.joboffer.dto.JobApplicationUpdateRequest;
import com.ybrainy.joboffer.entity.ApplicationStatus;
import com.ybrainy.joboffer.entity.JobApplication;
import com.ybrainy.joboffer.exception.BusinessException;
import com.ybrainy.joboffer.exception.ResourceNotFoundException;
import com.ybrainy.joboffer.mapper.JobApplicationMapper;
import com.ybrainy.joboffer.messaging.JobApplicationEventPublisher;
import com.ybrainy.joboffer.repository.JobApplicationRepository;
import com.ybrainy.joboffer.repository.JobOfferRepository;
import com.ybrainy.joboffer.service.JobApplicationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

  private static final Logger log = LoggerFactory.getLogger(JobApplicationServiceImpl.class);

  private final JobApplicationRepository applicationRepository;
  private final JobOfferRepository offerRepository;
  private final JobApplicationMapper mapper;
  private final JobApplicationEventPublisher eventPublisher;

  public JobApplicationServiceImpl(
      JobApplicationRepository applicationRepository,
      JobOfferRepository offerRepository,
      JobApplicationMapper mapper,
      JobApplicationEventPublisher eventPublisher) {
    this.applicationRepository = applicationRepository;
    this.offerRepository = offerRepository;
    this.mapper = mapper;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public JobApplicationResponse create(String offerId, JobApplicationRequest request) {
    String normalizedOfferId = offerId.trim();
    var offer =
        offerRepository
            .findById(normalizedOfferId)
            .orElseThrow(() -> new ResourceNotFoundException("Job offer not found: " + normalizedOfferId));

    String email = request.applicantEmail().trim().toLowerCase();
    if (applicationRepository.existsByOfferIdAndApplicantEmailIgnoreCase(normalizedOfferId, email)) {
      throw new BusinessException("You already applied to this offer with this email.");
    }

    JobApplication saved = applicationRepository.save(mapper.toEntity(normalizedOfferId, request));
    eventPublisher.publishCreated(saved, resolveOfferTitle(offer.getTitle()));
    return mapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobApplicationResponse> listByOffer(String offerId) {
    String normalizedOfferId = offerId.trim();
    if (!offerRepository.existsById(normalizedOfferId)) {
      throw new ResourceNotFoundException("Job offer not found: " + normalizedOfferId);
    }

    return applicationRepository.findByOfferIdOrderByCreatedAtDesc(normalizedOfferId).stream().map(mapper::toResponse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<JobApplicationResponse> listAll() {
    return applicationRepository.findAllByOrderByCreatedAtDesc().stream().map(mapper::toResponse).toList();
  }

  @Override
  @Transactional
  public JobApplicationResponse updateReview(String applicationId, JobApplicationUpdateRequest request) {
    JobApplication app =
        applicationRepository
            .findById(applicationId.trim())
            .orElseThrow(() -> new ResourceNotFoundException("Job application not found: " + applicationId));
    ApplicationStatus previousStatus = app.getStatus();
    mapper.applyReview(app, request);
    JobApplication saved = applicationRepository.save(app);

    if (previousStatus != saved.getStatus()) {
      String offerTitle =
          offerRepository.findById(saved.getOfferId()).map((offer) -> resolveOfferTitle(offer.getTitle())).orElse("Offre d'emploi");
      eventPublisher.publishStatusChanged(saved, offerTitle);

      if (saved.getStatus() == ApplicationStatus.ACCEPTED) {
        log.info("Acceptance notification scheduled asynchronously for application {}", saved.getId());
      }
    }

    return mapper.toResponse(saved);
  }

  @Override
  @Transactional
  public void delete(String applicationId) {
    JobApplication app =
        applicationRepository
            .findById(applicationId.trim())
            .orElseThrow(() -> new ResourceNotFoundException("Job application not found: " + applicationId));
    applicationRepository.delete(app);
  }

  private String resolveOfferTitle(String offerTitle) {
    return offerTitle == null || offerTitle.isBlank() ? "Offre d'emploi" : offerTitle;
  }
}
