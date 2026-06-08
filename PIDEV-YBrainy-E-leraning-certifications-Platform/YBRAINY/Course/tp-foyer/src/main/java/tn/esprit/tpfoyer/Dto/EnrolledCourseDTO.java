package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrolledCourseDTO {
    private Long courseId;
    private String courseTitle;
    private String thumbnailUrl;
    private String category;
    private String level;
    private Double completionPercentage;
    private String enrollmentStatus;
    private LocalDateTime enrollmentDate;
    private LocalDateTime completedAt;
    private String certificateId;
    private Double bestQuizScore;
    private Integer totalLessons;
    private Integer completedLessons;
}
