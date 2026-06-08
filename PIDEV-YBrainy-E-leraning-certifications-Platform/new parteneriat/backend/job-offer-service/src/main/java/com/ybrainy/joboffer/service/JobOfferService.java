package com.ybrainy.joboffer.service;

import com.ybrainy.joboffer.entity.OfferStatus;
import com.ybrainy.joboffer.dto.JobOfferRequest;
import com.ybrainy.joboffer.dto.JobOfferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobOfferService {

  JobOfferResponse create(JobOfferRequest request);

  JobOfferResponse update(String id, JobOfferRequest request);

  JobOfferResponse getById(String id);

  Page<JobOfferResponse> getAll(String partnershipId, OfferStatus status, String keyword, Pageable pageable);

  void delete(String id);
}
