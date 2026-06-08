package com.ybrainy.joboffer.repository;

import com.ybrainy.joboffer.entity.JobOffer;
import com.ybrainy.joboffer.entity.OfferStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferRepository extends JpaRepository<JobOffer, String> {

  Page<JobOffer> findByPartnershipId(String partnershipId, Pageable pageable);

  List<JobOffer> findAllByPartnershipId(String partnershipId);

  Page<JobOffer> findByStatus(OfferStatus status, Pageable pageable);

  Page<JobOffer> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
