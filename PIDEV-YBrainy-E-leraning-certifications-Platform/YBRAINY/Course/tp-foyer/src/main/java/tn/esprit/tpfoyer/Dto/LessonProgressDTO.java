package tn.esprit.tpfoyer.Dto;

import tn.esprit.tpfoyer.Entities.enums.ProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
