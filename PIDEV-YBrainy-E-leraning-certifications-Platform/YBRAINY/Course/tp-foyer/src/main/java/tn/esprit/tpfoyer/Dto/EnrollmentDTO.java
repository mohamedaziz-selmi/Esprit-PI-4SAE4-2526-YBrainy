package tn.esprit.tpfoyer.Dto;

import tn.esprit.tpfoyer.Entities.enums.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentDTO {

    private Long id;
    private Long studentId;
    private Long courseId;
    private LocalDateTime enrollmentDate;
    private EnrollmentStatus status;
    private Long currentLessonId;
    private Double completionPercentage;
    private String paymentIntentId;
    private LocalDateTime completedAt;
}
