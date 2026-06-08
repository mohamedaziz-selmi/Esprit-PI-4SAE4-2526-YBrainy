package com.ybrainy.partnership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partnerships")
public class Partnership {

  @Id
  private String id;

  @Column(nullable = false, length = 140)
  private String name;

  @Column(nullable = false, unique = true, length = 140)
  private String email;

  @Column(length = 30)
  private String phone;

  @Column(length = 255)
  private String website;

  @Column(length = 2000)
  private String description;

  @Column(nullable = false)
  private boolean active;

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
    if (!this.active) {
      this.active = true;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getWebsite() {
    return website;
  }

  public void setWebsite(String website) {
    this.website = website;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
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
