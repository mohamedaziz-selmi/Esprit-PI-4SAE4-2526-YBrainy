package com.ybrainy.joboffer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "job_offers")
public class JobOffer {

  @Id
  private String id;

  @Column(nullable = false, length = 180)
  private String title;

  @Column(nullable = false, length = 4000)
  private String description;

  @Column(length = 180)
  private String location;

  @Column(precision = 12, scale = 2)
  private BigDecimal salaryMin;

  @Column(precision = 12, scale = 2)
  private BigDecimal salaryMax;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContractType contractType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OfferStatus status;

  @Column
  private LocalDate deadline;

  @Column(nullable = false, length = 64)
  private String partnershipId;

  @Column(columnDefinition = "LONGTEXT")
  private String imageDataUrl;

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
    if (this.status == null) {
      this.status = OfferStatus.DRAFT;
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public BigDecimal getSalaryMin() {
    return salaryMin;
  }

  public void setSalaryMin(BigDecimal salaryMin) {
    this.salaryMin = salaryMin;
  }

  public BigDecimal getSalaryMax() {
    return salaryMax;
  }

  public void setSalaryMax(BigDecimal salaryMax) {
    this.salaryMax = salaryMax;
  }

  public ContractType getContractType() {
    return contractType;
  }

  public void setContractType(ContractType contractType) {
    this.contractType = contractType;
  }

  public OfferStatus getStatus() {
    return status;
  }

  public void setStatus(OfferStatus status) {
    this.status = status;
  }

  public LocalDate getDeadline() {
    return deadline;
  }

  public void setDeadline(LocalDate deadline) {
    this.deadline = deadline;
  }

  public String getPartnershipId() {
    return partnershipId;
  }

  public void setPartnershipId(String partnershipId) {
    this.partnershipId = partnershipId;
  }

  public String getImageDataUrl() {
    return imageDataUrl;
  }

  public void setImageDataUrl(String imageDataUrl) {
    this.imageDataUrl = imageDataUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
