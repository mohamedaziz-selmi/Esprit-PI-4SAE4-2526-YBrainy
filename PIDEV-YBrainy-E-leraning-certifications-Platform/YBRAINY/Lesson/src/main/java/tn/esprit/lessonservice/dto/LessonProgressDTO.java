package tn.esprit.lessonservice.dto;

import tn.esprit.lessonservice.entities.ProgressStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonProgressDTO {
    private Long id;
    private Long enrollmentId;
    private Long lessonId;
    private ProgressStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer timeSpentSeconds;
}
