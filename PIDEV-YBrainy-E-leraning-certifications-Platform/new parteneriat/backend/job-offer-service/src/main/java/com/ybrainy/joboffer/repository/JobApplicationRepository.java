package com.ybrainy.joboffer.repository;

import com.ybrainy.joboffer.entity.JobApplication;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, String> {

  boolean existsByOfferIdAndApplicantEmailIgnoreCase(String offerId, String applicantEmail);

  List<JobApplication> findByOfferIdOrderByCreatedAtDesc(String offerId);

  List<JobApplication> findAllByOrderByCreatedAtDesc();
}
