package com.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Time limit in minutes (0 = no limit) */
    private Integer timeLimit;

    /** Passing score percentage (e.g. 70) */
    private Integer passingScore;

    /** Number of attempts allowed (0 = unlimited) */
    private Integer maxAttempts;

    /** easy, medium, hard */
    private String difficulty;

    /** draft, published, archived */
    @Column(nullable = false)
    private String status;

    /** Total number of students who took this quiz */
    private Integer attemptCount;

    /** Average score across all attempts */
    private Double averageScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id")
    @JsonIgnore
    private Certification certification;

    public Long getCertificationId() {
        return certification != null ? certification.getId() : null;
    }
    public String getCertificationTitle() {
        return certification != null ? certification.getTitle() : null;
    }

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orderIndex ASC")
    private List<Question> questions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "draft";
        if (this.timeLimit == null) this.timeLimit = 30;
        if (this.passingScore == null) this.passingScore = 70;
        if (this.maxAttempts == null) this.maxAttempts = 3;
        if (this.attemptCount == null) this.attemptCount = 0;
        if (this.averageScore == null) this.averageScore = 0.0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

