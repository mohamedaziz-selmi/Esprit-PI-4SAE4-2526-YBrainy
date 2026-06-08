package com.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "student" or "teacher" */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 3000)
    private String description;

    /** Category: attendance, behavior, certification, performance, issue, feedback */
    @Column(nullable = false)
    private String category;

    /** Priority: low, medium, high, critical */
    private String priority;

    /** Status: open, in_progress, resolved, closed */
    private String status;

    /** Name of the student or teacher this report is about */
    private String subjectName;

    /** Optional: email of the subject */
    private String subjectEmail;

    /** Name of the person who submitted the report */
    private String submittedBy;

    /** Optional: linked course name */
    private String courseName;

    /** Optional: linked certification name */
    private String certificationName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "open";
        if (this.priority == null) this.priority = "medium";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

