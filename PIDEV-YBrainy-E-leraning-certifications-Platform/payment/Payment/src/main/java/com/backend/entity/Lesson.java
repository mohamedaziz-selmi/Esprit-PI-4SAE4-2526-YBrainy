package com.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lessons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Path to the uploaded lesson video */
    private String videoPath;

    /** Order within the course (used for drag & drop reordering) */
    @Column(nullable = false)
    private Integer orderIndex;

    private String duration;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @JsonIgnore
    private Course course;

    /** Expose course ID in JSON without circular ref */
    public Long getCourseId() {
        return course != null ? course.getId() : null;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.orderIndex == null) this.orderIndex = 0;
    }
}

