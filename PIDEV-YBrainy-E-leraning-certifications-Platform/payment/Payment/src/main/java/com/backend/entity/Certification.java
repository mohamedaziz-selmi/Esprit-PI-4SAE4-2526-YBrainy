package com.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "certifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 3000)
    private String description;

    /** Organization / platform issuing the certificate */
    private String issuedBy;

    /** e.g., Web Development, Data Science, Cybersecurity */
    private String category;

    /** beginner, intermediate, advanced */
    private String level;

    /** Estimated duration to complete, e.g. "40 hours", "6 weeks" */
    private String duration;

    /** Prerequisites description */
    @Column(length = 1000)
    private String prerequisites;

    /** Image/badge URL */
    private String badgeImageUrl;

    /** Passing score percentage required (e.g. 70) */
    private Integer passingScore;

    /** Number of students who earned this certification */
    private Integer earnedCount;

    /** active / inactive / draft */
    @Column(nullable = false)
    private String status;

    /** Link to a related course */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    @JsonIgnore
    private Course course;

    public Long getCourseId() {
        return course != null ? course.getId() : null;
    }
    public String getCourseTitle() {
        return course != null ? course.getTitle() : null;
    }

    @OneToMany(mappedBy = "certification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Quiz> quizzes = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "active";
        if (this.earnedCount == null) this.earnedCount = 0;
        if (this.passingScore == null) this.passingScore = 70;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

