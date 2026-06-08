package com.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String category;

    @Column(length = 2000)
    private String description;

    /** Path to the uploaded video used as the course thumbnail */
    private String thumbnailVideoPath;

    private Double rating;

    private Integer totalStudents;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Lesson> lessons = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.rating == null) this.rating = 0.0;
        if (this.totalStudents == null) this.totalStudents = 0;
    }
}

