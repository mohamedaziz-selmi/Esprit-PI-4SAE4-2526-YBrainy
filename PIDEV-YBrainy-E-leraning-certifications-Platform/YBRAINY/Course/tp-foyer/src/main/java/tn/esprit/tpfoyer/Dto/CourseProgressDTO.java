package tn.esprit.tpfoyer.Dto;

import tn.esprit.tpfoyer.Entities.enums.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseProgressDTO {

    private Long courseId;
    private Long enrollmentId;
    private Double completionPercentage;
    private EnrollmentStatus enrollmentStatus;
    private Long currentLessonId;
    private List<LessonProgressDTO> lessonProgresses;
}
