package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for course analytics data shown to instructors.
 * Provides insights about course engagement and student progress.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseAnalyticsDTO {
    
    private Long courseId;
    private String courseTitle;
    
    // Enrollment statistics
    private Integer totalEnrollments;
    private Integer activeStudentsLast7Days;
    private Integer activeStudentsLast30Days;
    
    // Lesson progress
    private Integer totalLessons;
    private Double averageProgressPercentage;
    private Integer completedStudents;
    
    // Engagement metrics
    private Double averageRating;
    private Integer totalRatings;
    private Integer totalReviews;
    
    // Content stats
    private Long totalVideoMinutes;
    private Integer totalPdfMaterials;
    private Integer totalImageMaterials;
    
    // Revenue (if applicable)
    private Double totalRevenue;
    private Double monthlyRevenue;

    // Time tracking
    private Long totalTimeSpentSeconds;
    private String totalTimeSpentFormatted;

    // Timestamps
    private String lastUpdated;
}
