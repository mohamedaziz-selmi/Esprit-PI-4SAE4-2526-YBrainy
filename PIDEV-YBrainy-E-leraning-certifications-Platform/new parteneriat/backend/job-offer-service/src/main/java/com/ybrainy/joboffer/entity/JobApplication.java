package com.ybrainy.joboffer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "job_applications",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_email", columnNames = {"offer_id", "applicant_email"}))
public class JobApplication {

  @Id
  private String id;

  @Column(name = "offer_id", nullable = false, length = 64)
  private String offerId;

  @Column(nullable = false, length = 160)
  private String applicantName;

  @Column(name = "applicant_email", nullable = false, length = 200)
  private String applicantEmail;

  @Column(length = 4000)
  private String message;

  @Column(columnDefinition = "LONGTEXT")
  private String cvDataUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ApplicationStatus status;

  @Column(length = 2000)
  private String reviewerNotes;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (this.id == null || this.id.isBlank()) {
      this.id = UUID.randomUUID().toString();
    }
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.applicantEmail != null) {
      this.applicantEmail = this.applicantEmail.trim().toLowerCase();
    }
    if (this.status == null) {
      this.status = ApplicationStatus.PENDING;
    }
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getOfferId() {
    return offerId;
  }

  public void setOfferId(String offerId) {
    this.offerId = offerId;
  }

  public String getApplicantName() {
    return applicantName;
  }

  public void setApplicantName(String applicantName) {
    this.applicantName = applicantName;
  }

  public String getApplicantEmail() {
    return applicantEmail;
  }

  public void setApplicantEmail(String applicantEmail) {
    this.applicantEmail = applicantEmail;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getCvDataUrl() {
    return cvDataUrl;
  }

  public void setCvDataUrl(String cvDataUrl) {
    this.cvDataUrl = cvDataUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public ApplicationStatus getStatus() {
    return status;
  }

  public void setStatus(ApplicationStatus status) {
    this.status = status;
  }

  public String getReviewerNotes() {
    return reviewerNotes;
  }

  public void setReviewerNotes(String reviewerNotes) {
    this.reviewerNotes = reviewerNotes;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
