package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardDTO {
    private Long studentId;
    private int totalEnrolled;
    private int totalCompleted;
    private int totalInProgress;
    private int totalCertificates;
    private double averageProgress;
    private List<EnrolledCourseDTO> enrolledCourses;
}
