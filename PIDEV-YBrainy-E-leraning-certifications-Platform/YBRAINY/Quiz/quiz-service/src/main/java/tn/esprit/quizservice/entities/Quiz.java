package tn.esprit.quizservice.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quiz_course", columnList = "course_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "passing_score", nullable = false)
    @Builder.Default
    private Integer passingScore = 70;

    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
