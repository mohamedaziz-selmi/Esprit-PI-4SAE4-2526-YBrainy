package com.ybrainy.joboffer.mapper;

import com.ybrainy.joboffer.entity.JobOffer;
import com.ybrainy.joboffer.dto.JobOfferRequest;
import com.ybrainy.joboffer.dto.JobOfferResponse;
import org.springframework.stereotype.Component;

@Component
public class JobOfferMapper {

  public JobOffer toEntity(JobOfferRequest req) {
    JobOffer offer = new JobOffer();
    apply(offer, req);
    return offer;
  }

  public void apply(JobOffer target, JobOfferRequest req) {
    target.setTitle(req.title().trim());
    target.setDescription(req.description().trim());
    target.setLocation(req.location() == null ? null : req.location().trim());
    target.setImageDataUrl(req.imageDataUrl());
    target.setSalaryMin(req.salaryMin());
    target.setSalaryMax(req.salaryMax());
    target.setContractType(req.contractType());
    target.setStatus(req.status());
    target.setDeadline(req.deadline());
    target.setPartnershipId(req.partnershipId().trim());
  }

  public JobOfferResponse toResponse(JobOffer offer) {
    return toResponse(offer, null, null);
  }

  public JobOfferResponse toResponse(JobOffer offer, String partnershipName, String partnershipEmail) {
    return new JobOfferResponse(
        offer.getId(),
        offer.getTitle(),
        offer.getDescription(),
        offer.getLocation(),
        offer.getImageDataUrl(),
        offer.getSalaryMin(),
        offer.getSalaryMax(),
        offer.getContractType(),
        offer.getStatus(),
        offer.getDeadline(),
        offer.getPartnershipId(),
        partnershipName,
        partnershipEmail,
        offer.getCreatedAt(),
        offer.getUpdatedAt());
  }
}
