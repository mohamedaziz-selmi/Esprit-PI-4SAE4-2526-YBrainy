package com.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Meet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    /** The URL to join the meeting (Zoom, Google Meet, Teams, etc.) */
    @Column(nullable = false)
    private String meetLink;

    /** Meeting start date/time */
    @Column(nullable = false)
    private LocalDateTime startTime;

    /** Meeting end date/time */
    private LocalDateTime endTime;

    /** Color label for the calendar (e.g. bg-primary, bg-success) */
    private String color;

    private LocalDateTime createdAt;

    /** Optional: link to a course */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    @JsonIgnore
    private Course course;

    /** Expose course ID in JSON without circular ref */
    public Long getCourseId() {
        return course != null ? course.getId() : null;
    }

    /** Expose course title in JSON */
    public String getCourseTitle() {
        return course != null ? course.getTitle() : null;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.color == null) this.color = "bg-primary";
    }
}

