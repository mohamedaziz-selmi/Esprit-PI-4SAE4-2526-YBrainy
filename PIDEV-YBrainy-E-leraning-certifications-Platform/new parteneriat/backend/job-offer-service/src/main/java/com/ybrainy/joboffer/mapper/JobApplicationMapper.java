package com.ybrainy.joboffer.mapper;

import com.ybrainy.joboffer.dto.JobApplicationRequest;
import com.ybrainy.joboffer.dto.JobApplicationResponse;
import com.ybrainy.joboffer.dto.JobApplicationUpdateRequest;
import com.ybrainy.joboffer.entity.ApplicationStatus;
import com.ybrainy.joboffer.entity.JobApplication;
import org.springframework.stereotype.Component;

@Component
public class JobApplicationMapper {

  public JobApplication toEntity(String offerId, JobApplicationRequest req) {
    JobApplication app = new JobApplication();
    app.setOfferId(offerId.trim());
    app.setApplicantName(req.applicantName().trim());
    app.setApplicantEmail(req.applicantEmail().trim().toLowerCase());
    app.setMessage(req.message() == null ? null : req.message().trim());
    app.setCvDataUrl(req.cvDataUrl());
    app.setStatus(ApplicationStatus.PENDING);
    app.setReviewerNotes(null);
    return app;
  }

  public void applyReview(JobApplication app, JobApplicationUpdateRequest req) {
    app.setStatus(req.status());
    app.setReviewerNotes(req.reviewerNotes() == null ? null : req.reviewerNotes().trim());
  }

  public JobApplicationResponse toResponse(JobApplication app) {
    return new JobApplicationResponse(
        app.getId(),
        app.getOfferId(),
        app.getApplicantName(),
        app.getApplicantEmail(),
        app.getMessage(),
        app.getCvDataUrl(),
        app.getStatus(),
        app.getReviewerNotes(),
        app.getCreatedAt());
  }
}
