package com.ybrainy.partnership.controller;

import com.ybrainy.partnership.client.JobOfferClient;
import com.ybrainy.partnership.dto.ExistsResponse;
import com.ybrainy.partnership.dto.JobOfferSummary;
import com.ybrainy.partnership.dto.PartnershipRequest;
import com.ybrainy.partnership.dto.PartnershipResponse;
import com.ybrainy.partnership.service.PartnershipService;
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
@RequestMapping("/api/partnerships")
public class PartnershipController {

  private final PartnershipService service;
  private final JobOfferClient jobOfferClient;

  public PartnershipController(PartnershipService service, JobOfferClient jobOfferClient) {
    this.service = service;
    this.jobOfferClient = jobOfferClient;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PartnershipResponse create(@Valid @RequestBody PartnershipRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  public PartnershipResponse update(@PathVariable String id, @Valid @RequestBody PartnershipRequest request) {
    return service.update(id, request);
  }

  @GetMapping("/{id}")
  public PartnershipResponse getById(@PathVariable String id) {
    return service.getById(id);
  }

  @GetMapping("/{id}/with-offers")
  public PartnershipWithOffersResponse getByIdWithOffers(@PathVariable String id) {
    PartnershipResponse partnership = service.getById(id);
    List<JobOfferSummary> offers = jobOfferClient.findByPartnership(id, 0, 50);
    int totalOffers = offers.size();
    return new PartnershipWithOffersResponse(partnership, offers, totalOffers);
  }

  @GetMapping
  public Page<PartnershipResponse> getAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "DESC") String direction,
      @RequestParam(required = false) String search) {
    Sort sort = "ASC".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    Pageable pageable = PageRequest.of(page, size, sort);
    return service.getAll(search, pageable);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @GetMapping("/{id}/exists")
  public ExistsResponse exists(@PathVariable String id) {
    return new ExistsResponse(service.existsById(id));
  }

  public record PartnershipWithOffersResponse(
      PartnershipResponse partnership,
      List<JobOfferSummary> offers,
      int totalOffers) {
  }
}
