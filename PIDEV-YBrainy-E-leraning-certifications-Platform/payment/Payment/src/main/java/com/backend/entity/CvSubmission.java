package com.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cv_submissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CvSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String phone;

    /** Position / role the user is applying for */
    private String position;

    /** Related course or certification */
    private String courseName;

    /** Cover letter / motivation text */
    @Column(length = 3000)
    private String coverLetter;

    /** Path to uploaded CV file (PDF, DOCX, etc.) */
    private String cvFilePath;

    /** Original file name for display */
    private String cvFileName;

    /** Status: pending, reviewed, shortlisted, accepted, rejected */
    @Column(nullable = false)
    private String status;

    /** Education level: bachelor, master, phd, diploma, certificate */
    private String educationLevel;

    /** Years of experience */
    private Integer yearsOfExperience;

    /** Skills comma-separated */
    private String skills;

    /** Reviewer notes (internal) */
    @Column(length = 2000)
    private String reviewerNotes;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        this.submittedAt = LocalDateTime.now();
        if (this.status == null) this.status = "pending";
    }
}

