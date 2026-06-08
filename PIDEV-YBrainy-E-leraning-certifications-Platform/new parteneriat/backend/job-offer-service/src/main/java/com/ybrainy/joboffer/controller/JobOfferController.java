package com.ybrainy.joboffer.controller;

import com.ybrainy.joboffer.entity.OfferStatus;
import com.ybrainy.joboffer.dto.JobApplicationRequest;
import com.ybrainy.joboffer.dto.JobApplicationResponse;
import com.ybrainy.joboffer.dto.JobOfferRequest;
import com.ybrainy.joboffer.dto.JobOfferResponse;
import com.ybrainy.joboffer.service.JobApplicationService;
import com.ybrainy.joboffer.service.JobOfferService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offers")
public class JobOfferController {

  private final JobOfferService service;
  private final JobApplicationService applicationService;

  public JobOfferController(JobOfferService service, JobApplicationService applicationService) {
    this.service = service;
    this.applicationService = applicationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public JobOfferResponse create(@Valid @RequestBody JobOfferRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  public JobOfferResponse update(@PathVariable String id, @Valid @RequestBody JobOfferRequest request) {
    return service.update(id, request);
  }

  @GetMapping("/{id}")
  public JobOfferResponse getById(@PathVariable String id) {
    return service.getById(id);
  }

  @GetMapping
  public Page<JobOfferResponse> getAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "DESC") String direction,
      @RequestParam(required = false) String partnershipId,
      @RequestParam(required = false) OfferStatus status,
      @RequestParam(required = false) String keyword) {
    Sort sort = "ASC".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    Pageable pageable = PageRequest.of(page, size, sort);
    return service.getAll(partnershipId, status, keyword, pageable);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @PostMapping("/{offerId}/applications")
  @ResponseStatus(HttpStatus.CREATED)
  public JobApplicationResponse apply(@PathVariable String offerId, @Valid @RequestBody JobApplicationRequest request) {
    return applicationService.create(offerId, request);
  }

  @GetMapping("/{offerId}/applications")
  public List<JobApplicationResponse> getApplicationsByOffer(@PathVariable String offerId) {
    return applicationService.listByOffer(offerId);
  }
}
