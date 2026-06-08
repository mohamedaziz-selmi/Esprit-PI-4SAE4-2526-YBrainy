package tn.esprit.lessonservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long enrollmentId;
    private Long lessonId;

    @Enumerated(EnumType.STRING)
    private ProgressStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long timeSpentSeconds;
    private LocalDateTime lastActivityAt;
}
